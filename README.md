# Klub POS – pokladna pro soukromý klub (Android)

Nativní Android aplikace (Kotlin + Jetpack Compose), zatím laděná pro telefon, layout se automaticky přepne na tablet (≥ 600 dp boční navigace, detail člena ve dvou sloupcích).

## Jak spustit
1. Otevři složku `klubpos` v **Android Studiu** (Koala / Ladybug nebo novější), počkej na Gradle sync.
2. Spusť na telefonu / emulátoru (min. Android 8.0, API 26).
3. Při prvním startu se nahrají ukázkoví členové a položky (`seedIfEmpty()` v `MainActivity` – před ostrým provozem smazat).

> Projekt jsem nemohl zkompilovat (neměl jsem Android SDK), takže první sync může chtít drobné úpravy verzí knihoven.

## Co umí
| Obrazovka | Funkce |
|---|---|
| **Členové** | Mřížka medailonků (fotka, jméno/přezdívka). Modrý rámeček + štítek „V KLUBU“ = otevřený účet. Barevný pruh = převažující stav. Řádky: *Dnes X kr* / *Dnes zaplaceno ✓* / *Dluh Y kr* (z minulých dnů) / *Přeplatek*. Filtry Všichni / V klubu / S dluhem, hledání, přidání člena, „Ukončit večer“ (zavře všem účty, dluhy zůstanou). |
| **Detail člena** | Příchod/Odchod, zapisování položek (klepnutí = 1 ks, podržení = množství nebo jiná cena), vlastní položka s ručně zadanou cenou, platba (Vše / Jen dnešek, volitelně zavřít účet), historie se stornem, úprava/archivace, fotka přes QR. |
| **Sklad** | Stav zásob, zvýraznění toho, co dochází, naskladnění, inventura (zadání skutečného stavu), tlačítko „→ Nákup“. |
| **Nákup** | Nákupní seznam: automatické doplnění všeho pod minimem (navržené množství), ruční položky, úprava množství, odškrtnutím rovnou naskladnit, sdílení seznamu (WhatsApp, SMS…). |
| **Položky** | Katalog: název, kategorie, cena v kreditech, sledování skladu, jednotka, minimum, obvyklé nákupní množství, skrytí, obrázek přes QR. |

## Logika účtu (útrata na dluh)
- Vše v **kreditech**, 1 kr = 1 Kč, ukládáno jako celé číslo (`Long`).
- Účet je **neměnná kniha pohybů** (`ledger`): zápis = +, platba = −. Chyby se opravují **stornem** (opravný záznam), nic se nemaže → dohledatelnost.
- **„Dnes“ = obchodní den od 06:00** (`BusinessDay.CUTOFF_HOUR`), takže pivo ve 2 ráno patří ještě k večeru.
- Dnešní platby se nejdřív započtou na dnešní útratu. Díky tomu se rozliší: *dnešní útrata nezaplacená*, *dnes zaplaceno*, *dnes zaplaceno, ale zůstává starý dluh*.
- „Otevřený účet / je v klubu“ = otevřená návštěva (`visits`). Otevře se ručně nebo automaticky prvním zápisem; zavře se odchodem, platbou se zaškrtnutím, nebo „Ukončit večer“.
- Prodej sledovaného zboží automaticky ubere sklad a při poklesu pod minimum ho rovnou přidá na nákupní seznam.

## Šifrování
- **Databáze**: celý soubor šifruje SQLCipher (AES-256). Náhodný 256bit klíč je uložen v `EncryptedSharedPreferences`, chráněných master klíčem v **Android Keystore** (na většině tabletů hardwarově).
- **Fotky a obrázky**: každý jako `EncryptedFile` (AES-256-GCM), zmenšené na 512 px (položky 256 px).
- Zálohy do cloudu i přenos zařízení jsou vypnuté (`allowBackup=false`, `data_extraction_rules`) – data neopustí tablet.
- ⚠️ Důsledek: **odinstalace nebo reset tabletu = ztráta dat**. Je potřeba přidat šifrovaný export/zálohu (viz níže).

## Nahrávání fotky přes QR
Tablet spustí na chvíli malý webový server (NanoHTTPD) a ukáže QR s adresou `http://<ip-tabletu>:<port>/u/<token>`. Telefon na **stejné Wi-Fi** QR naskenuje běžným fotoaparátem, otevře stránku, vyfotí/vybere fotku a odešle. Tablet ji zmenší, zašifruje a uloží; server se vypne.
Zabezpečení: náhodný jednorázový token, platnost 5 min, limit 10 MB, server běží jen při otevřeném dialogu. Přenos je v rámci lokální sítě bez TLS – pro uzavřenou klubovou Wi-Fi v pořádku; když bude potřeba, dá se doplnit self-signed HTTPS.

## Nastavení a aktualizace z GitHubu
V **Nastavení → O aplikaci** je verze, číslo buildu a odkaz na repozitář `Jedn0ta/ZapoKredit`. Tlačítko **Zkontrolovat aktualizace** se zeptá GitHubu na poslední vydání (Releases); pokud je novější, ukáže poznámky k vydání, stáhne APK a předá ho instalátoru Androidu. Data zůstanou zachována.

### Jak vydat novou verzi
1. **Jednorázově** vytvoř podpisový klíč (uschovej ho! bez něj nepůjde tablet aktualizovat):
   ```
   keytool -genkeypair -v -keystore zapokredit.jks -alias zapokredit -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 zapokredit.jks    # (macOS: base64 -i zapokredit.jks)
   ```
2. V GitHubu: *Settings → Secrets and variables → Actions* přidej `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
3. Vydání = tag: `git tag v0.2.0 && git push origin v0.2.0`. Workflow `.github/workflows/release.yml` sestaví podepsané APK a založí Release.
4. Na tablet nainstaluj **poprvé** APK z Releases ručně (ne verzi z Android Studia – ta je podepsaná debug klíčem a aktualizace přes ni by selhala). Dál už stačí tlačítko v Nastavení.

Poznámky: repozitář musí zůstat **veřejný** (aplikace se ptá bez přihlášení; soukromý repo by potřeboval token). Při první aktualizaci Android požádá o povolení „Instalovat neznámé aplikace“ pro tuto aplikaci.

## Struktura
```
cz.klubpos
├─ core/crypto     DatabaseKeyManager (klíč v Keystore), ImageStore (šifrované obrázky)
├─ data/db         Room entity, DAO, AppDatabase (SQLCipher)
├─ data/repo       ClubRepository – veškerá obchodní logika (zápis, platba, storno, sklad, nákup)
├─ domain          MemberStatus (výpočet stavu medailonku), BusinessDay, formát kreditů
├─ upload          PhotoUploadServer (QR upload), QrGenerator
├─ update          UpdateManager – kontrola, stažení a instalace z GitHub Releases
└─ ui              Compose obrazovky (vč. Nastavení) + téma
```

## Co ještě chybí / další kroky
1. **Záloha a export** (šifrovaný soubor na USB/Drive) – kvůli riziku ztráty dat nejdůležitější.
2. **Přístup barmana** – PIN/role (barman vs. správce), log kdo co zapsal.
3. **Kiosk režim** na tabletu (screen pinning / dedikované zařízení), případně `FLAG_SECURE`.
4. **Reporty**: tržby za večer/měsíc, nejprodávanější položky, seznam dlužníků.
5. Rozpad lahví na panáky (rum 4 cl ↔ lahev 0,7 l) – zatím jsou lihoviny bez skladové evidence.
6. Přesná verze Androidu a model tabletu → doladit `minSdk`/`targetSdk`, velikost mřížky.
7. ViewModely + testy jednotek pro `MemberStatus` a `ClubRepository` (logika je už oddělená, takže snadné).
