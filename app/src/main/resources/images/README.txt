==========================================
  ICONITA APLICATIE - MarcmanMixer
==========================================

Iconita oficiala este `mixer.ico`.

1. Creeaza sau exporta iconita (ideal include dimensiuni 16, 32, 48, 128, 256 px)
   - Nume fisier: mixer.ico
   - Format: ICO, cu canal alpha
   - Tema: perfume bottle, lab equipment, sau "M" stylish

2. Copiaza fisierul `mixer.ico` in acest director:
   `app/src/main/resources/images/mixer.ico`

3. (Optional) Adauga si varianta PNG fallback:
   - `icon.png` (256x256) pentru teste rapide sau compatibilitate veche

4. Recompileaza aplicatia:
   `mvn clean install`

5. Iconita va aparea:
   - In colțul ferestrei aplicației
   - In taskbar Windows
   - In Alt+Tab
   - Pe executabilul final (.exe)

==========================================
ALTERNATIVE:
- icon.png (format PNG) - fallback in cazul in care nu exista varianta .ico
- icon@2x.png (512x512) - pentru rezoluții mari
==========================================

Aplicatia va rula normal chiar daca iconita nu este prezenta.






















