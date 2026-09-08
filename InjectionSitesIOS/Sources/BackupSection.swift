import SwiftUI
import UniformTypeIdentifiers

extension UTType { static let insofinaBackup = UTType(exportedAs: "com.example.insofina.backup", conformingTo: .data) }
struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.insofinaBackup] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws {
        guard let bytes = configuration.file.regularFileContents, bytes.count <= BackupCodec.maximumBytes else { throw AppError.invalidData }
        data = bytes
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
struct BackupSection: View {
    @EnvironmentObject var store: StateStore
    var onImported: () -> Void = {}
    @State private var importing = false
    @State private var exporting = false
    @State private var passwordSheet = false
    @State private var exportMode = true
    @State private var password = ""
    @State private var confirmation = ""
    @State private var message: String?
    @State private var messageIsError = false
    @State private var busy = false
    @State private var incomingData: Data?
    @State private var document = BackupDocument(data: Data())
    @State private var pending: DecodedBackup?
    @State private var preview = false
    @State private var replace = false
    @State private var confirmReplace = false
    var body: some View {
        Section("Backup dei dati") {
            Text("Trasferisci storico, sensori e impostazioni tra dispositivi Android e iOS con un file protetto e la sua password. L’app non invia dati a server. Per conservarli soltanto in locale, scegli «Sul mio iPhone» nel selettore documenti.").font(.caption)
            Button("Esporta backup") { exportMode = true; password = ""; confirmation = ""; message = nil; passwordSheet = true }.disabled(busy || store.loadFailed)
            Button("Importa backup") { importing = true }.disabled(busy || store.loadFailed)
            if busy { ProgressView("Operazione in corso…") }
            if let message { Text(message).foregroundStyle(messageIsError ? .red : .green) }
        }
        .fileExporter(isPresented: $exporting, document: document, contentType: .insofinaBackup, defaultFilename: "InSofina-backup.insofia-backup") { result in
            switch result { case .success: report("Backup esportato", error: false); case .failure(let error): report(error.localizedDescription, error: true) }
            document = BackupDocument(data: Data())
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.insofinaBackup, .data, .json]) { result in
            do {
                let url = try result.get()
                let scope = url.startAccessingSecurityScopedResource()
                defer { if scope { url.stopAccessingSecurityScopedResource() } }
                incomingData = try BackupCodec.readFile(url)
                exportMode = false; password = ""; confirmation = ""; message = nil; passwordSheet = true
            } catch { report(error.localizedDescription, error: true) }
        }
        .sheet(isPresented: $passwordSheet, onDismiss: { password = ""; confirmation = "" }) {
            NavigationStack {
                Form {
                    Text(exportMode ? "Scegli una password di almeno 8 caratteri." : "Inserisci la password usata per proteggere il file.")
                    SecureField("Password", text: $password).textContentType(exportMode ? .newPassword : .password).autocorrectionDisabled()
                    if exportMode { SecureField("Conferma password", text: $confirmation).textContentType(.newPassword) }
                    if let message, messageIsError { Text(message).foregroundStyle(.red) }
                    Button("Continua", action: prepare).disabled(busy)
                    if busy { ProgressView() }
                }.navigationTitle(exportMode ? "Proteggi il backup" : "Apri il backup")
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annulla") { incomingData = nil; passwordSheet = false }.disabled(busy) } }
            }.interactiveDismissDisabled(busy)
        }
        .sheet(isPresented: $preview, onDismiss: { pending = nil; retainedPassword = ""; replace = false }) {
            NavigationStack {
                Form {
                    if let pending {
                        Section("Anteprima backup") {
                            Text("Data: \(Date(milliseconds: pending.exportedAt).formatted(date: .numeric, time: .shortened))")
                            Text("Versione app: \(pending.appVersion) · schema \(pending.schemaVersion)")
                            Text("Storico: \(pending.state.records.count) elementi")
                            Text("Sensori: \(pending.state.records.filter { $0.mode == .SENSORE }.count)")
                            Text("Avatar: \(pending.state.avatar.label)")
                            Text("Le impostazioni e l’avatar saranno quelli del backup, anche in modalità unione.")
                        }
                        Picker("Modalità importazione", selection: $replace) {
                            Text("Unisci con i dati esistenti").tag(false)
                            Text("Sostituisci tutti i dati").tag(true)
                        }.pickerStyle(.inline)
                        Button("Importa") { if replace { confirmReplace = true } else { finishImport() } }.disabled(busy)
                        if busy { ProgressView("Importazione…") }
                        if let message, messageIsError { Text(message).foregroundStyle(.red) }
                    }
                }.navigationTitle("Anteprima backup")
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annulla") { preview = false }.disabled(busy) } }
                    .alert("Sostituire tutti i dati?", isPresented: $confirmReplace) {
                        Button("Sostituisci", role: .destructive, action: finishImport)
                        Button("Annulla", role: .cancel) {}
                    } message: { Text("Storico e impostazioni saranno sostituiti. Prima verrà creato un backup di sicurezza cifrato nella memoria privata dell’app.") }
            }.interactiveDismissDisabled(busy)
        }
    }
    @State private var retainedPassword = ""
    private func report(_ text: String, error: Bool) { message = text; messageIsError = error }
    private func prepare() {
        if exportMode && (password.utf16.count < 8 || password != confirmation) { report("Usa almeno 8 caratteri e due password identiche.", error: true); return }
        guard !password.isEmpty else { report("Inserisci la password del backup.", error: true); return }
        busy = true; message = nil
        let secret = password, state = store.state, data = incomingData, isExport = exportMode
        Task {
            do {
                if isExport {
                    let output = try await Task.detached(priority: .userInitiated) { try BackupCodec.write(state, password: secret) }.value
                    document = BackupDocument(data: output); passwordSheet = false
                    busy = false
                    // Let the password sheet dismiss before presenting the native picker.
                    try? await Task.sleep(for: .milliseconds(350))
                    exporting = true
                } else {
                    guard let data else { throw AppError.invalidData }
                    let decoded = try await Task.detached(priority: .userInitiated) { try BackupCodec.read(data, password: secret) }.value
                    pending = decoded; retainedPassword = secret; incomingData = nil; replace = false; passwordSheet = false; busy = false
                    try? await Task.sleep(for: .milliseconds(350))
                    preview = true
                }
                password = ""; confirmation = ""
            } catch { busy = false; report(error.localizedDescription, error: true) }
        }
    }
    private func finishImport() {
        guard let pending else { return }
        busy = true; message = nil
        do {
            let count = try store.apply(pending.state, replace: replace, password: retainedPassword)
            onImported()
            report("Backup importato: \(count) elementi importati", error: false)
            preview = false
        } catch { report(error.localizedDescription, error: true) }
        busy = false
    }
}
