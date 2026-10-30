# Gnoflemail

Gnoflemail è un client di posta elettronica per Android, semplice e rispettoso della privacy, sviluppato da **Alessandro Bronchi / ABGnofle Software** (<https://www.gnofle.it/software/gnoflemail>).

È basato sul codice open source di [Thunderbird per Android](https://github.com/thunderbird/thunderbird-android) (a sua volta derivato da K-9 Mail), distribuito con licenza Apache 2.0.

> Gnoflemail è un progetto indipendente. Non è affiliato a Mozilla né a MZLA Technologies Corporation. Thunderbird è un marchio di MZLA Technologies Corporation.

*English: Gnoflemail is an independent, privacy-friendly Android email client based on the open source code of Thunderbird for Android (Apache License 2.0). Not affiliated with Mozilla or MZLA.*

## Caratteristiche principali

- Schermata iniziale con l'elenco di tutte le caselle, i messaggi non letti e l'ora dell'ultima sincronizzazione
- Account IMAP e POP3 multipli
- Gmail tramite password per app
- Nessuna pubblicità, nessuna telemetria, nessun tracciamento
- Dimensione del testo dell'elenco regolabile con i tasti del volume

## Principali modifiche rispetto a Thunderbird per Android

Come richiesto dalla licenza Apache 2.0 (sezione 4), ecco un riepilogo dei cambiamenti apportati ai file originali:

- Nome dell'app, identificativo del pacchetto (`it.gnofle.gnoflemail`), logo e icone
- Nuova schermata iniziale con l'elenco delle caselle (`AccountListActivity`)
- Nuova schermata "Supporta Gnoflemail" e schermata "Informazioni" personalizzata
- Accessi OAuth ereditati da Thunderbird disattivati; guida per Gmail con password per app
- Rimossi i riferimenti al servizio Thundermail
- Testi di benvenuto e crediti aggiornati

## Come compilare

Servono Java 21 e l'Android SDK (non serve Android Studio).

```bash
./gradlew assembleFossRelease     # APK
./gradlew bundleFossRelease       # AAB per Google Play
```

La compilazione richiede molta memoria: se Gradle si ferma, aumenta lo swap e usa `org.gradle.jvmargs=-Xmx4g` in `~/.gradle/gradle.properties`.

### Firma di rilascio

Senza una chiave, la versione di rilascio viene firmata con la chiave di debug (solo per prove personali). Per firmare con la propria chiave, aggiungi a `~/.gradle/gradle.properties` (file fuori dal repository, mai da pubblicare):

```
GNOFLE_STORE_FILE=/percorso/della/chiave.jks
GNOFLE_STORE_PASSWORD=...
GNOFLE_KEY_ALIAS=...
GNOFLE_KEY_PASSWORD=...
```

## Licenza

Apache License 2.0, vedi i file `LICENSE` e `NOTICE`. Il copyright del codice originale resta ai rispettivi autori (Thunderbird / MZLA Technologies Corporation e collaboratori di K-9 Mail). Le modifiche sono © 2026 Alessandro Bronchi / ABGnofle Software.

## Contatti

Assistenza, segnalazioni e suggerimenti: software@gnofle.it
