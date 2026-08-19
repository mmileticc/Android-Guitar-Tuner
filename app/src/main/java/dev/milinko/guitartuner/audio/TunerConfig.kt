package dev.milinko.guitartuner.audio

/**
 * Centralizovano mesto za sve pragove/konstante koje audio engine (AudioAnalyzer)
 * i logika obrade (TunerViewModel) koriste. Cilj: da se sve "magic numbers" podešavaju
 * na jednom mestu i da se AudioAnalyzer i TunerViewModel ne rasilaze u pragovima
 * (npr. ranije je postojao MIN_VOLUME_THRESHOLD = 0.008f u AudioAnalyzer-u i
 * hardkodovano 0.015f u ViewModel-u istovremeno).
 */
object TunerConfig {

    // --- Audio capture ---
    const val SAMPLE_RATE = 44100
    const val BUFFER_SIZE = 4096
    const val OVERLAP = 2048

    // --- Opseg gitarskih frekvencija koje uopšte razmatramo (uključuje 7-icu / drop tuning-e) ---
    const val PITCH_MIN_HZ = 55f   // ~A1, ostavlja margine ispod Drop D (D2=73.4Hz)
    const val PITCH_MAX_HZ = 700f  // pokriva harmoničke overtone-ove i visoke bendove

    // --- Pouzdanost YIN detekcije ---
    const val MIN_PROBABILITY = 0.80f

    // --- Jačina signala (RMS): HISTEREZIS umesto jednog praga.
    // Da bi se UŠLO u aktivno stanje (nova nota) treba jači signal (izbegava lažne okidače
    // od šuma/civije/kucanja); dok se VEĆ prati aktivna nota, dovoljan je slabiji signal da
    // se nastavi praćenje kroz prirodno bledenje (decay) žice bez gubitka - upravo taj gubitak
    // je ranije pravio "iglu koja leti" kad zvuk počne da bledi.
    const val MIN_VOLUME_THRESHOLD_ENTER = 0.015f
    const val MIN_VOLUME_THRESHOLD_HOLD = 0.006f

    // --- Koliko ms posle poslednje VALIDNE detekcije čekamo pre nego što proglasimo tišinu.
    // Dok smo unutar ovog prozora, igla se JEDNOSTAVNO DRŽI na poslednjoj dobroj vrednosti
    // (ne ažurira se sumnjivim/šumnim frejmovima) - nema više veštačkog fade-out niza brojeva
    // koji se mešao sa pravim očitavanjima i pravio haos u igli. ---
    const val SILENCE_HOLD_MS = 400L

    // --- Volume smoothing (samo za vizuelni pulsirajući krug, ne utiče na pitch tačnost) ---
    const val VOLUME_SMOOTHING = 0.8f

    // --- Attack phase: prvih N ms po pojavi signala ignorišemo (izbegava haos pri udaru žice) ---
    const val ATTACK_IGNORE_MS = 100L

    // --- Cents-bazirani "jump" filter (zamena za stari apsolutni Hz prag) ---
    // Skok veći od ovoga (u centima) se smatra sumnjivim i mora da se ponovi MAX_JUMPS puta
    // pre nego što ga prihvatimo kao stvarnu promenu tona.
    const val JUMP_THRESHOLD_CENTS = 80f
    const val MAX_JUMPS = 3

    // --- Note lock histerezis: koliko centi odstupanja od zaključane note je potrebno
    // da bismo "otključali" i prešli na drugu (ciljnu) notu ---
    const val NOTE_LOCK_HYSTERESIS_CENTS = 35f

    // --- Dok je neka žica već zaključana, koliko centi tolerancije ima PRIORITET nad
    // pretragom svih ostalih žica - sprečava da harmonik/šum tokom bledenja tona odvuče
    // detekciju na potpuno drugu žicu (žice su realno razmaknute >380 centi, pa je 150
    // sigurna margina koja i dalje hvata normalna netačna štimovanja iste žice). ---
    const val LOCKED_NOTE_SEARCH_WINDOW_CENTS = 150f

    // --- Kada se smatra da je žica naštimovana (za boje, haptiku, itd.) ---
    const val IN_TUNE_THRESHOLD_CENTS = 5f

    // --- "Dead zone" oko 0 centi da igla ne treperi kad je praktično idealno ---
    const val DEAD_ZONE_CENTS = 1.5f

    // --- Referentna frekvencija A4 (podrazumevano standardnih 440Hz, podesivo 438-445Hz) ---
    const val DEFAULT_REFERENCE_PITCH = 440f
    const val REFERENCE_PITCH_MIN = 438f
    const val REFERENCE_PITCH_MAX = 445f
}
