# Analisi del porting Android → iOS

## Sorgenti analizzati e piano

L’app Android consiste in `MainActivity.kt`, `BackupManager.kt`, un test JUnit,
27 PNG anatomici, icona e risorse XML, manifest, configurazione Gradle e workflow.
Non contiene database remoto, servizi, Bluetooth o letture di sensori hardware:
«Sensore» è una registrazione manuale della posizione.

Piano adottato: ricostruire schema e regole temporali; implementare modello e
archivio atomico; trasferire geometrie e UI; implementare il formato cifrato;
aggiungere test di regressione e interoperabilità, progetto riproducibile e CI.

## Modelli, schermate e persistenza Android

`AvatarStyle`: `UOMO`, `DONNA`, `YETI` (predefinito). `EntryMode`: `INSULINA`,
`SENSORE`. `InsulinType`: `RAPIDA`, `BASALE`, assente per i sensori.

| Area persistita | Etichetta | Zone |
| --- | --- | --- |
| RIGHT_ARM | Braccio destro | 4 |
| LEFT_ARM | Braccio sinistro | 4 |
| ABDOMEN | Addome | 8 |
| RIGHT_THIGH | Coscia destra | 4 |
| LEFT_THIGH | Coscia sinistra | 4 |
| RIGHT_GLUTE | Gluteo destro | 2 |
| LEFT_GLUTE | Gluteo sinistro | 2 |

Gli indici persistiti partono da zero; quelli visualizzati da uno. Braccia e
cosce usano superiore/inferiore e interna/esterna, glutei superiore/inferiore.
Nell’addome, le colonne 0–1 sono descritte come sinistra e le colonne 2–3 come
destra, conservando gli indici: è la correzione di `displayZoneName` Android.

Android usa SharedPreferences `injection_sites`, storico JSON in
`injection_history`, vecchio sensore singolo in `sensor_position`, avatar in
`avatar_style`; impostazioni in `red`, `orange`, `yellow`, `sensorStage`,
`sensorHidden`. La migrazione del vecchio sensore è interna ad Android e il
backup esporta già l’elenco unificato. iOS importa tale elenco, non i file
SharedPreferences Android.

Home mostra figura frontale, posteriore, legenda, elenco delle sette aree.
Ogni area offre selezione modalità/tipo, data/ora, zone, salvataggio e conferma
di date future. Storico permette cancellazione confermata. Impostazioni
permettono avatar, soglie, ripristino e backup. La UI è interamente italiana.

## Geometrie, specchio e decisione sui dettagli

I sei file `drawable-nodpi/avatar_{man,woman,yeti}_{front,back}.png` sono copiati
direttamente nell’asset catalog a scala 1×, senza ricompressione o riflessione.
`asset-manifest.json` contiene dimensioni e hash degli originali.

`frontOutline` e `backOutline` Android definiscono poligoni normalizzati.
Ogni zona è l’intersezione fra il contorno e una cella della sua griglia, non
un rettangolo selezionabile sovrapposto alla figura. La colonna del braccio
sinistro è invertita; le altre seguono gli indici della dashboard Android.
Lo stesso poligono SwiftUI viene usato per disegnare e per `contains`.
L’alpha originale del PNG limita la pittura; una maschera binaria dello stesso
alpha limita il tocco allo stesso supporto visibile. Sono maschere di sfondo,
non una nuova segmentazione anatomica o una modifica delle coordinate.

Sono state rilevate due incoerenze preesistenti da non trasferire silenziosamente:

1. Android inverte le colonne di `LEFT_THIGH` nel dettaglio, ma non nella
   dashboard. Lo stesso indice si sposta quindi da un lato all’altro.
2. I 21 PNG di dettaglio hanno proporzioni diverse dalla tela 300×310 sulla
   quale Android definisce `zoomOutline`. L’ispezione del braccio originale
   mostra che il contorno di dettaglio può arrivare fino alla mano.

Per rispettare l’uso dei sei PNG e mantenere coerenti disegno/tocco/indici,
iOS ingrandisce il contorno **della dashboard** per ogni coppia avatar/area:
21 schermate, con contesto anatomico attorno alla zona. L’ingrandimento avviene
in fase di visualizzazione; non vengono salvati PNG ritagliati. La trasformazione
è una scala uniforme più traslazione, applicata alla figura, ai poligoni e alle
posizioni degli indicatori; il tocco usa la sua inversa. Le coordinate Android
restano intatte. Le vecchie coordinate dedicate sono conservate nel JSON solo
come riferimento verificabile e non vengono disegnate.

Questa scelta conserva la numerazione della dashboard Android anche nei dettagli
iOS e **non replica la discrepanza della coscia sinistra**. Non vengono migrati
o reinterpretati gli indici dei record esistenti. Per un record proveniente dal
vecchio dettaglio Android resta quindi l’ambiguità già presente fra le due viste
Android: il backup non registra quale vista fosse stata usata.

## Date, disponibilità e sensori

`eventDateTime` è un `Int64` in millisecondi Unix, indipendente dal fuso.
La visualizzazione usa il calendario/fuso del dispositivo e italiano; la
selezione azzera secondi e millisecondi. `createdAt` è tecnico e serve soltanto
da spareggio. Storico e sensore corrente seguono lo stesso confronto decrescente.

Iniezioni: default rosso <12 h, arancione <24 h, giallo <36 h, poi verde;
si usa l’iniezione con data effettiva più recente nella zona. Date future hanno
età zero. Soglie positive e strettamente crescenti.

Sensori: giorni interi trascorsi, stadi default 0–9, 10–19, 20–29, 30–39;
dal giorno 40 nascosti. Colori originali `475569`, `7C8796`, `B8C0CB`, `DEE3EA`.
Il sensore corrente ha opacità 1; gli altri 0,38. Al massimo tre per zona,
ordinati per evento/creazione. Durata stadio >0, scomparsa dopo tre stadi.
Le visualizzazioni temporali si aggiornano ogni minuto, come Android.

## Formato esatto del backup, schema 1

Estensione: `.insofia-backup` (grafia diversa dal nome visualizzato InSofina,
conservata intenzionalmente). JSON UTF-8 esterno:

```json
{
  "format": "insofia-encrypted-backup",
  "schemaVersion": 1,
  "kdf": "PBKDF2WithHmacSHA256",
  "iterations": 210000,
  "salt": "<Base64 standard senza a capo>",
  "cipher": "AES-256-GCM",
  "iv": "<Base64 standard senza a capo>",
  "ciphertext": "<Base64 di ciphertext concatenato al tag>"
}
```

PBKDF2 usa HMAC-SHA256, password UTF-8, 210.000 iterazioni, chiave 256 bit,
salt casuale di 16 byte. AES-GCM usa IV/nonce casuale di 12 byte, tag di 16 byte,
nessun AAD. Android `Cipher.doFinal` produce `ciphertext || tag`: iOS concatena
esplicitamente `sealed.ciphertext + sealed.tag`, **non** usa `combined`, che
includerebbe anche il nonce. Lettura limitata a 32 MiB e 100.000 record;
iterazioni importabili 100.000–2.000.000, salt di almeno 16 byte.

Il testo decifrato contiene `schemaVersion`, `appVersion`, `exportedAt`
(millisecondi Unix positivi), `payloadJson` (stringa JSON, non oggetto) e
`payloadSha256` (Base64 di SHA-256 dei byte UTF-8 esatti di `payloadJson`).
L’ordine delle chiavi JSON non è significativo; il digest viene controllato
prima di decodificare o riserializzare il payload.

Payload: `schemaVersion`, `records`, `settings`, `avatar`. Impostazioni:
`redHours`, `orangeHours`, `yellowHours` (Float Android), `sensorStageDays`,
`sensorHiddenDays` (Int64). Avatar e valori enum conservano la forma maiuscola.

Ogni record contiene `id`, `area`, `zone`, `mode`, `time`, `createdAt` e, quando
presente, `insulinType`. Il campo esportato resta **`time`**, alias Android della
data effettiva. Il lettore accetta anche `eventDateTime`, con precedenza su
`time`. `createdAt` assente usa la data dell’evento. Identificativi mancanti
usano l’UUID v3 MD5 di Java, senza namespace, del testo
`area|zone|mode|insulinType-o-null|eventDateTime|createdAt`, così le successive
importazioni del vecchio record non generano duplicati.

Non è emerso un componente crittografico che richieda un secondo formato.
La prova con CryptoKit/CommonCrypto resta però subordinata al test macOS.
La CI usa il codec Android originale in entrambe le direzioni e una password
con accento ed emoji per coprire anche la conversione Unicode.

## Importazione e sicurezza locale

Il file viene decifrato e validato completamente prima dell’anteprima. Unione:
gli ID locali prevalgono, vengono aggiunti soltanto ID nuovi; impostazioni e
avatar sono quelli importati, come Android. Sostituzione: conferma separata,
backup cifrato locale dello stato precedente, poi scrittura atomica dell’intero
stato. Un errore conserva memoria e file precedenti. Le password non sono
registrate nei log o salvate in preferenze; i campi UI vengono svuotati alla
chiusura dei dialoghi. Nessuna credenziale reale è inclusa nei test.
