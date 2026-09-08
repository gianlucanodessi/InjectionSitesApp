# InSofina per iOS

Implementazione Swift/SwiftUI per iOS 17+, preparata sulla branch
`ios-implementation`. Il progetto Android e il relativo workflow non sono stati
modificati. Non sono stati creati commit, push, PR, merge o distribuzioni.

Il bundle identifier provvisorio è **`com.example.insofina.ios`**, configurabile
in `project.yml` (`PRODUCT_BUNDLE_IDENTIFIER`). Prima di una distribuzione reale
andrà scelto quello definitivo: cambiarlo dopo l’installazione crea un diverso
contenitore locale. Non è stato registrato alcun identificativo Apple.

## Comportamento implementato

- Dashboard fronte/retro, scelta Uomo/Donna/Yeti e 21 dettagli anatomici.
- Sei PNG originali, identici byte per byte; proporzioni preservate. I dettagli
  ingrandiscono le zone della dashboard senza creare nuovi PNG.
- Zone poligonali, numerazione e tocco condividono la trasformazione della figura;
  la trasparenza originale esclude lo sfondo anche dal rilevamento del tocco.
- Vista a specchio: `RIGHT_*` a destra dello schermo, `LEFT_*` a sinistra;
  descrizioni dell’addome corrette per lo stesso riferimento.
- Registrazione Insulina (Rapida/Basale) o Sensore; calendario, rotelle 00–23 e
  00–59; conferma per date future; conferma visibile dopo scrittura riuscita.
- Storico ordinato per data dell’evento e poi inserimento, cancellazione confermata,
  sensore corrente, fino a tre indicatori per zona e quattro stadi di sfumatura.
- Impostazioni con validazione, valori predefiniti, pulsante verde «Salvato»,
  messaggi d’errore e barra di salvataggio dentro la safe area.
- Backup `.insofia-backup`, password in campo protetto, selettore documenti,
  anteprima, unione predefinita, deduplicazione, sostituzione confermata e backup
  locale cifrato di sicurezza prima della sostituzione.

La decisione sulle immagini di dettaglio e l’incongruenza preesistente nella
numerazione Android sono spiegate in `PORTING.md`. Non vengono riscritti gli
identificativi dello storico importato.

## Persistenza

Storico, impostazioni e avatar sono un’unica transazione JSON in
`Library/Application Support/InSofina/state.json`, con `.atomic` e protezione
file iOS completa. Lo stato SwiftUI viene pubblicato dopo il ritorno della
scrittura. Un errore di lettura non causa il salvataggio di uno storico vuoto.
La directory privata è esclusa dal backup automatico del sistema.

Gli aggiornamenti con lo stesso bundle identifier mantengono il contenitore;
la disinstallazione rimuove i dati. I backup di sicurezza privati sono nella
stessa directory. L’app non contiene client di rete, analytics o sincronizzazione.
Il selettore documenti può mostrare provider configurati dall’utente: scegliere
«Sul mio iPhone» mantiene anche il file esportato in locale.

## Generazione e test su macOS

Occorrono Xcode con un simulatore iOS 17 o successivo, JDK 17, Python con Pillow
e XcodeGen 2.42.0. La configurazione segue la
[specifica ufficiale XcodeGen 2.42.0](https://github.com/yonaskolb/XcodeGen/blob/2.42.0/Docs/ProjectSpec.md).
La CI compila la versione fissata di XcodeGen dal sorgente.

Dalla radice del repository:

```bash
python3 InjectionSitesIOS/scripts/verify_port.py
python3 InjectionSitesIOS/scripts/prepare_interop.py
bash InjectionSitesAndroid_v2/gradlew -p "$PWD/InjectionSitesIOS/Interop" run \
  --args="generate $PWD/InjectionSitesIOS/Tests/Fixtures/android.insofia-backup"
xcodegen generate --spec InjectionSitesIOS/project.yml
xcrun simctl list devices available
```

Poi usare l’UDID di un iPhone Simulator nell’argomento `-destination`:

```bash
xcodebuild -project InjectionSitesIOS/InSofina.xcodeproj \
  -scheme InSofina -destination 'platform=iOS Simulator,id=UDID_DEL_SIMULATORE' \
  -derivedDataPath InjectionSitesIOS/build/DerivedData \
  -resultBundlePath InjectionSitesIOS/build/InSofina.xcresult \
  -parallel-testing-enabled NO CODE_SIGNING_ALLOWED=NO build test
```

Usare un nuovo percorso `.xcresult` a ogni esecuzione se esiste già.
Il file Android di prova viene generato **dal vero `BackupManager.kt`**, copiato
senza modifiche nell’harness JVM. Solo Context, preferenze e Base64 Android
sono adattati al runner; schema, serializzazione e crittografia sono quelli
del repository. Le preferenze stub rifiutano le scritture: non sono un test
della persistenza Android. La password Unicode del fixture è pubblica e serve
esclusivamente a dati sintetici.

Dopo i test, il confronto inverso si può eseguire così:

```bash
CONTAINER=$(xcrun simctl get_app_container UDID_DEL_SIMULATORE com.example.insofina.ios data)
bash InjectionSitesAndroid_v2/gradlew -p "$PWD/InjectionSitesIOS/Interop" run \
  --args="verify $CONTAINER/Documents/ios-export.insofia-backup"
```

Il workflow separato `../.github/workflows/ios-build.yml` automatizza questi
passaggi su macOS, compresi i test UI, e conserva log e `.xcresult` come artefatti.
Non richiede certificati, account Apple o segreti. La compatibilità crittografica
con le API Apple non va considerata verificata finché questa esecuzione non passa.

## Verifiche effettivamente eseguite qui

- Verifica Python di identità binaria, SHA-256 e dimensioni dei sei PNG.
- Confronto dei 42 contorni estratti (21 dashboard e 21 dettagli di riferimento)
  con il sorgente Android; iOS disegna solo quelli della dashboard.
- Verifica pixel per pixel delle sei maschere di trasparenza per il tocco.
- Lettura dei JSON dell’asset catalog e generazione dell’harness Android.
- Ispezione visiva di una tavola con i 21 ingrandimenti, generata dagli originali
  e dai contorni; è una verifica delle risorse, **non uno screenshot SwiftUI**.
- Controllo delle modifiche Git: nessun file Android o workflow Android modificato.

**Non eseguiti su Windows:** compilazione Swift/Xcode, XCTest, XCUITest,
generazione del fixture tramite JVM/JDK 17 e confronto bidirezionale con le API
Apple. Non è stato avviato GitHub Actions perché le modifiche restano locali e
non pubblicate. La configurazione e i test sono preparati, ma il loro esito su
macOS resta da verificare. Anche la revisione visiva finale su iPhone/iPad resta
da effettuare nel simulatore o su dispositivo.

`FILES.md` elenca tutti i file aggiunti, esclusi i prodotti generati ignorati da Git.
