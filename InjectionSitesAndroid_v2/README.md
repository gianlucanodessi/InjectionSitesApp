# InjectionSitesAndroid v2

Versione corretta e allineata alle schermate concordate.

- Home con corpo intero / aree cliccabili
- Braccio destro: 4 zone dalla spalla al bicipite
- Braccio sinistro: 4 zone dalla spalla al bicipite
- Addome: 8 quadranti
- Gluteo destro: 2 zone
- Gluteo sinistro: 2 zone
- Gamba destra: pagina dedicata, 4 zone SOLO anteriori sul quadricipite
- Gamba sinistra: pagina dedicata, 4 zone SOLO anteriori sul quadricipite
- Scelta Insulina / Sensore
- Se Insulina: Rapida / Basale
- Sensore: cerchietto grigio
- Storico

## Build riproducibile

Il progetto usa un bootstrapper Gradle testuale e deve essere compilato con **JDK 17**. Il bootstrapper scarica Gradle 8.7 al primo avvio in `GRADLE_USER_HOME` (oppure in `~/.gradle`) e non richiede di versionare il binario `gradle-wrapper.jar`.

1. Installa Android SDK Platform 35 e i Build Tools corrispondenti.
2. Configura il percorso locale dell'SDK in `local.properties` (non versionato), ad esempio `sdk.dir=/percorso/android-sdk`.
3. Assicurati che `JAVA_HOME` punti a un JDK 17.
4. Esegui dalla cartella del progetto:

```bash
./gradlew :app:assembleDebug
```

L'APK debug viene generato in `app/build/outputs/apk/debug/app-debug.apk`.

> Il primo avvio richiede accesso a `https://services.gradle.org` per scaricare Gradle 8.7. In una CI senza accesso internet, pre-popola `GRADLE_USER_HOME/bootstrap/gradle-8.7` con la distribuzione estratta di Gradle 8.7.
