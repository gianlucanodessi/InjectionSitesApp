import SwiftUI

@main @MainActor struct InSofinaApp: App {
    @StateObject private var store = StateStore.live()
    var body: some Scene {
        WindowGroup {
            HomeScreen().environmentObject(store).environment(\.locale, Locale(identifier: "it_IT"))
                .tint(Color(hex: 0x1557C0))
                .alert("Attenzione", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) {
                    Button("OK") { store.error = nil }
                } message: { Text(store.error ?? "") }
        }
    }
}
struct HomeScreen: View {
    @EnvironmentObject var store: StateStore
    @State private var path: [BodyArea] = []
    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Seleziona una zona sulla sagoma").foregroundStyle(.secondary)
                    Text("Vista a specchio").font(.headline)
                    Text("Il lato destro dello schermo è il tuo lato destro.").font(.caption)
                    BodyDiagram(state: store.state) { area, _ in path.append(area) }
                    Text("Vista posteriore · glutei").font(.headline)
                    BodyDiagram(state: store.state, back: true) { area, _ in path.append(area) }
                    AvailabilityLegend(settings: store.state.settings)
                    Text("Tutte le aree").font(.title2.bold())
                    ForEach(BodyArea.allCases) { area in
                        NavigationLink(value: area) {
                            HStack { VStack(alignment: .leading) { Text(area.label).bold(); Text("\(area.zoneCount) zone selezionabili").font(.caption) }; Spacer(); Image(systemName: "chevron.right") }.padding().background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
                        }
                    }
                }.padding()
            }
            .navigationTitle("Nuova iniezione")
            .navigationDestination(for: BodyArea.self) { AreaScreen(area: $0) }
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    NavigationLink { HistoryScreen() } label: { Image(systemName: "clock.arrow.circlepath").accessibilityLabel("Storico") }
                    NavigationLink { SettingsScreen(state: store.state) } label: { Image(systemName: "gearshape").accessibilityLabel("Impostazioni") }
                }
            }
        }
    }
}
struct AvailabilityLegend: View {
    let settings: TimingSettings
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Disponibilità").bold()
            let labels = ["< \(settings.redHours.formatted()) h", "\(settings.redHours.formatted())–\(settings.orangeHours.formatted()) h", "\(settings.orangeHours.formatted())–\(settings.yellowHours.formatted()) h", "≥ \(settings.yellowHours.formatted()) h"]
            ForEach(0..<4) { i in HStack { Circle().fill(Visuals.availabilityColors[i]).frame(width: 10, height: 10); Text(labels[i]) } }
            Text("Sensori: grigio progressivamente più chiaro; quello corrente è evidenziato.").font(.caption)
        }.font(.subheadline).padding().frame(maxWidth: .infinity, alignment: .leading).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
    }
}
struct AreaScreen: View {
    @EnvironmentObject var store: StateStore
    let area: BodyArea
    @State private var mode = EntryMode.INSULINA
    @State private var insulin = InsulinType.RAPIDA
    @State private var zone: Int?
    @State private var date = Calendar.current.dateInterval(of: .minute, for: Date())!.start
    @State private var saved = false
    @State private var future = false
    @State private var timePicker = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Vista a specchio · \(store.state.avatar.label)").font(.headline)
                Picker("Tipo di registrazione", selection: $mode) { ForEach(EntryMode.allCases, id: \.self) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
                if mode == .INSULINA {
                    Picker("Tipo di insulina", selection: $insulin) { ForEach(InsulinType.allCases, id: \.self) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
                }
                Text("Quando è avvenuto l’evento").bold()
                DatePicker("Data", selection: $date, displayedComponents: .date).datePickerStyle(.graphical)
                Button { timePicker = true } label: { Label("Ora: \(EventTime.text(for: date))", systemImage: "clock") }
                BodyDiagram(state: store.state, detail: area, selected: zone) { _, index in zone = index; saved = false }
                TimelineView(.periodic(from: .now, by: 60)) { timeline in
                    VStack(spacing: 10) {
                        ForEach(0..<area.zoneCount, id: \.self) { index in
                            Button { zone = index; saved = false } label: {
                                HStack {
                                    Text("\(index + 1)").bold().foregroundStyle(.white).frame(width: 32, height: 32).background(Visuals.color(state: store.state, area: area, zone: index, now: timeline.date.milliseconds), in: Circle())
                                    Text(area.zoneName(index)); Spacer()
                                    if !Visuals.markers(state: store.state, area: area, zone: index, now: timeline.date.milliseconds).isEmpty { Image(systemName: "sensor.tag.radiowaves.forward.fill").accessibilityLabel("Sensore presente") }
                                    if zone == index { Image(systemName: "checkmark.circle.fill").foregroundStyle(.green) }
                                }.padding(10).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                            }.buttonStyle(.plain)
                        }
                    }
                }
                if saved { Text(mode == .INSULINA ? "Iniezione salvata." : "Sensore salvato.").foregroundStyle(.green).accessibilityIdentifier("eventSaved") }
            }.padding()
        }
        .navigationTitle(area.label)
        .safeAreaInset(edge: .bottom) {
            Button(saved ? "Salvato" : "Salva posizione") { if date > Date() { future = true } else { save() } }
                .buttonStyle(.borderedProminent).tint(saved ? .green : Color(hex: 0x1557C0)).disabled(zone == nil || saved || store.loadFailed)
                .frame(maxWidth: .infinity).padding().background(.bar)
        }
        .onChange(of: date) { _, _ in saved = false }
        .onChange(of: mode) { _, _ in saved = false }
        .onChange(of: insulin) { _, _ in saved = false }
        .sheet(isPresented: $timePicker) { TimeWheel(date: $date) }
        .alert("Data futura", isPresented: $future) {
            Button("Salva") { save() }; Button("Annulla", role: .cancel) {}
        } message: { Text("La data e l’ora selezionate sono nel futuro. Vuoi salvare comunque?") }
    }
    private func save() {
        guard let zone else { return }
        do {
            let event = Calendar.current.dateInterval(of: .minute, for: date)!.start
            try store.add(RecordItem(area: area, zone: zone, mode: mode, insulinType: mode == .INSULINA ? insulin : nil, eventDateTime: event.milliseconds))
            saved = true
        } catch { saved = false; store.error = error.localizedDescription }
    }
}
struct TimeWheel: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var date: Date
    @State private var hour = 0
    @State private var minute = 0
    var body: some View {
        NavigationStack {
            HStack {
                Picker("Ora", selection: $hour) { ForEach(0..<24) { Text(String(format: "%02d", $0)).tag($0) } }
                Text(":")
                Picker("Minuti", selection: $minute) { ForEach(0..<60) { Text(String(format: "%02d", $0)).tag($0) } }
            }.pickerStyle(.wheel).padding()
                .navigationTitle("Seleziona l’ora")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Annulla") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("Conferma") {
                        if let result = EventTime.replacingTime(in: date, hour: hour, minute: minute) { date = result }
                        dismiss()
                    } }
                }
        }.presentationDetents([.medium]).onAppear { hour = Calendar.current.component(.hour, from: date); minute = Calendar.current.component(.minute, from: date) }
    }
}
struct HistoryScreen: View {
    @EnvironmentObject var store: StateStore
    @State private var deleting: RecordItem?
    var body: some View {
        List {
            if store.state.records.isEmpty { Text("Nessuna registrazione").foregroundStyle(.secondary) }
            ForEach(ordered(store.state.records)) { record in
                HStack {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(record.area.label).bold()
                        Text("\(record.area.zoneName(record.zone)) · \(record.mode.label)")
                        if let insulin = record.insulinType { Text(insulin.label).foregroundStyle(insulin == .RAPIDA ? .orange : .blue) }
                        Text(Date(milliseconds: record.eventDateTime).formatted(date: .numeric, time: .shortened)).font(.caption)
                    }
                    Spacer()
                    Button(role: .destructive) { deleting = record } label: { Image(systemName: "trash").accessibilityLabel("Elimina registrazione") }.buttonStyle(.borderless)
                }.listRowBackground(Color(hex: record.mode == .SENSORE ? 0xE5E7EB : record.insulinType == .RAPIDA ? 0xE5F5E9 : 0xF0E6FA))
            }
        }.navigationTitle("Storico")
            .alert("Eliminare registrazione?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
                Button("Elimina", role: .destructive) { if let record = deleting { do { try store.delete(record) } catch { store.error = error.localizedDescription } }; deleting = nil }
                Button("Annulla", role: .cancel) { deleting = nil }
            } message: { Text("Questa operazione non può essere annullata.") }
    }
}
