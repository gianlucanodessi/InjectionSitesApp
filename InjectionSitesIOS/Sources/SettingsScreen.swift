import SwiftUI

struct SettingsScreen: View {
    @EnvironmentObject var store: StateStore
    @State private var avatar: AvatarStyle
    @State private var red: String
    @State private var orange: String
    @State private var yellow: String
    @State private var stage: String
    @State private var hidden: String
    @State private var saved = false
    @State private var error: String?
    init(state: AppState) {
        _avatar = State(initialValue: state.avatar)
        _red = State(initialValue: String(state.settings.redHours)); _orange = State(initialValue: String(state.settings.orangeHours)); _yellow = State(initialValue: String(state.settings.yellowHours))
        _stage = State(initialValue: String(state.settings.sensorStageDays)); _hidden = State(initialValue: String(state.settings.sensorHiddenDays))
    }
    var body: some View {
        Form {
            Section("Avatar") {
                Picker("Avatar", selection: $avatar) { ForEach(AvatarStyle.allCases) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
                Text("La scelta cambia solo la grafica, non lo storico.").font(.caption)
            }
            Section("Tempi colori iniezione (ore)") {
                field("Rosso fino a", text: $red); field("Arancione fino a", text: $orange); field("Giallo fino a", text: $yellow)
                Text("Le soglie devono essere crescenti; dopo l’ultima la zona diventa verde.").font(.caption)
            }
            Section("Tempi sensore (giorni)") {
                field("Durata stadio", text: $stage); field("Scomparsa", text: $hidden)
                Text("Tre stadi della durata indicata, poi grigio tenue fino alla scomparsa.").font(.caption)
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
            Section { Button("Ripristina valori predefiniti") { reload(TimingSettings()); save() } }
            BackupSection(onImported: { saved = false })
        }
        .navigationTitle("Impostazioni")
        .safeAreaInset(edge: .bottom) {
            VStack {
                if saved { Text("Impostazioni salvate").foregroundStyle(.green) }
                Button(saved ? "Salvato" : "Salva impostazioni", action: save).buttonStyle(.borderedProminent).tint(saved ? .green : Color(hex: 0x1557C0)).disabled(store.loadFailed).accessibilityIdentifier("saveSettings")
            }.frame(maxWidth: .infinity).padding().background(.bar)
        }
        .onChange(of: avatar) { _, value in
            saved = false
            do { try store.save(settings: store.state.settings, avatar: value) } catch { self.error = error.localizedDescription }
        }
        .onChange(of: store.state.settings) { _, value in reload(value) }
        .onChange(of: store.state.avatar) { _, value in avatar = value }
    }
    private func field(_ label: String, text: Binding<String>) -> some View {
        HStack { Text(label); Spacer(); TextField(label, text: Binding(get: { text.wrappedValue }, set: { text.wrappedValue = $0; saved = false; error = nil })).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(maxWidth: 120) }
    }
    private func reload(_ value: TimingSettings) {
        red = String(value.redHours); orange = String(value.orangeHours); yellow = String(value.yellowHours); stage = String(value.sensorStageDays); hidden = String(value.sensorHiddenDays)
    }
    private func save() {
        saved = false; error = nil
        let value = TimingSettings(redHours: Float(red.replacingOccurrences(of: ",", with: ".")) ?? -1, orangeHours: Float(orange.replacingOccurrences(of: ",", with: ".")) ?? -1, yellowHours: Float(yellow.replacingOccurrences(of: ",", with: ".")) ?? -1, sensorStageDays: Int64(stage) ?? -1, sensorHiddenDays: Int64(hidden) ?? -1)
        guard value.valid else { error = "Le soglie devono essere crescenti e il sensore deve avere tre stadi prima della scomparsa."; return }
        do { try store.save(settings: value, avatar: avatar); saved = true } catch { self.error = error.localizedDescription }
    }
}
