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

enum BackupOperation: String, Identifiable {
    case export, importData
    var id: String { rawValue }
}

// Only buttons live in the Form's Section. The SettingsScreen root owns the sheet.
struct BackupSection: View {
    @EnvironmentObject var store: StateStore
    let open: (BackupOperation) -> Void
    var body: some View {
        Section("Backup dei dati") {
            Text("Trasferisci storico, sensori e impostazioni tra dispositivi Android e iOS con un file protetto e la sua password. L’app non invia dati a server. Per conservarli soltanto in locale, scegli «Sul mio iPhone» nel selettore documenti.").font(.caption)
            Button("Esporta backup") { open(.export) }.disabled(store.loadFailed)
            Button("Importa backup") { open(.importData) }.disabled(store.loadFailed)
        }
    }
}

@MainActor final class BackupFlowModel: ObservableObject {
    let operation: BackupOperation
    @Published var password = ""
    @Published var confirmation = ""
    @Published private(set) var busy = false
    @Published private(set) var message: String?
    @Published private(set) var messageIsError = false
    @Published private(set) var document: BackupDocument?
    @Published private(set) var pending: DecodedBackup?
    @Published private(set) var filename: String?
    private var incomingData: Data?
    private(set) var retainedPassword = ""

    init(operation: BackupOperation) { self.operation = operation }

    func report(_ text: String, error: Bool) { message = text; messageIsError = error }

    func load(_ url: URL) async {
        guard !busy else { return }
        busy = true; message = nil; messageIsError = false; incomingData = nil; filename = nil
        defer { busy = false }
        do {
            incomingData = try await Task.detached(priority: .userInitiated) {
                let scope = url.startAccessingSecurityScopedResource()
                defer { if scope { url.stopAccessingSecurityScopedResource() } }
                return try BackupCodec.readFile(url)
            }.value
            filename = url.lastPathComponent
        } catch { report(error.localizedDescription, error: true) }
    }

    func prepare(state: AppState) async {
        guard !busy else { return }
        message = nil; messageIsError = false
        if operation == .export && (password.utf16.count < 8 || password != confirmation) {
            report("Usa almeno 8 caratteri e due password identiche.", error: true); return
        }
        guard !password.isEmpty else { report("Inserisci la password del backup.", error: true); return }
        if operation == .importData && incomingData == nil {
            report("Seleziona il file di backup da importare.", error: true); return
        }
        busy = true
        defer { busy = false }
        let secret = password
        do {
            if operation == .export {
                let bytes = try await Task.detached(priority: .userInitiated) {
                    try BackupCodec.write(state, password: secret)
                }.value
                // Keep the document alive through picker cancellation/retry.
                // There is no dismissal or timed presentation handoff.
                document = BackupDocument(data: bytes)
            } else if let data = incomingData {
                pending = try await Task.detached(priority: .userInitiated) {
                    try BackupCodec.read(data, password: secret)
                }.value
                retainedPassword = secret
                incomingData = nil
            }
            password = ""; confirmation = ""
        } catch { report(error.localizedDescription, error: true) }
    }

    func apply(to store: StateStore, replace: Bool) -> Bool {
        guard !busy, let pending else { return false }
        busy = true
        defer { busy = false }
        do {
            let count = try store.apply(pending.state, replace: replace, password: retainedPassword)
            report("Backup importato: \(count) elementi importati", error: false)
            self.pending = nil; retainedPassword = ""
            return true
        } catch { report(error.localizedDescription, error: true); return false }
    }
}

@MainActor struct BackupFlow: View {
    @EnvironmentObject var store: StateStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model: BackupFlowModel
    var onImported: () -> Void
    @State private var importing = false
    @State private var exporting = false
    @State private var replace = false
    @State private var confirmReplace = false
    @State private var imported = false

    init(operation: BackupOperation, onImported: @escaping () -> Void) {
        _model = StateObject(wrappedValue: BackupFlowModel(operation: operation))
        self.onImported = onImported
    }

    var body: some View {
        NavigationStack {
            Form {
                if imported {
                    Button("Fine") { dismiss() }
                } else if let pending = model.pending {
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
                    Button("Importa") { if replace { confirmReplace = true } else { finishImport() } }.disabled(model.busy)
                } else if model.document != nil {
                    Text("Backup pronto").accessibilityIdentifier("backupReady")
                    Text("Scegli dove salvare il file protetto.")
                    Button("Salva backup") { exporting = true }.accessibilityIdentifier("saveBackupFile")
                } else {
                    if model.operation == .importData {
                        Button("Seleziona file di backup") { importing = true }.disabled(model.busy)
                        if let filename = model.filename { Text(filename) }
                    }
                    Text(model.operation == .export ? "Scegli una password di almeno 8 caratteri." : "Seleziona il file e inserisci la password usata per proteggerlo.")
                    SecureField("Password", text: $model.password)
                        .textContentType(model.operation == .export ? .newPassword : .password)
                        .autocorrectionDisabled().accessibilityIdentifier("backupPassword")
                    if model.operation == .export {
                        SecureField("Conferma password", text: $model.confirmation)
                            .textContentType(.newPassword).accessibilityIdentifier("backupPasswordConfirmation")
                    }
                    Button("Continua") { Task { await model.prepare(state: store.state) } }
                        .disabled(model.busy).accessibilityIdentifier("prepareBackup")
                }
                if model.busy { ProgressView("Operazione in corso…").accessibilityIdentifier("backupBusy") }
                if let message = model.message { Text(message).foregroundStyle(model.messageIsError ? .red : .green) }
            }
            .navigationTitle(model.pending != nil ? "Anteprima backup" : model.operation == .export ? "Proteggi il backup" : "Apri il backup")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(model.document != nil || imported ? "Fine" : "Annulla") { dismiss() }
                        .disabled(model.busy || importing || exporting).accessibilityIdentifier("closeBackup")
                }
            }
            .alert("Sostituire tutti i dati?", isPresented: $confirmReplace) {
                Button("Sostituisci", role: .destructive, action: finishImport)
                Button("Annulla", role: .cancel) {}
            } message: { Text("Storico e impostazioni saranno sostituiti. Prima verrà creato un backup di sicurezza cifrato nella memoria privata dell’app.") }
        }
        .interactiveDismissDisabled(model.busy || importing || exporting)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.insofinaBackup, .data, .json]) { result in
            switch result {
            case .success(let url): Task { await model.load(url) }
            case .failure(let error): model.report(error.localizedDescription, error: true)
            }
        }
        .fileExporter(isPresented: $exporting, document: model.document, contentType: .insofinaBackup, defaultFilename: "InSofina-backup.insofia-backup") { result in
            switch result {
            case .success: model.report("Backup esportato", error: false)
            case .failure(let error): model.report(error.localizedDescription, error: true)
            }
        }
    }

    private func finishImport() {
        if model.apply(to: store, replace: replace) { onImported(); imported = true }
    }
}
