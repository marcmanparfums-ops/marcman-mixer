==========================================
  ICONITA APLICATIE - MarcmanMixer
==========================================

Pentru a seta iconita aplicatiei:

1. Pregateste o imagine PNG (recomandat 256x256 pixels sau 512x512)
   - Nume fisier: icon.png
   - Format: PNG cu transparenta
   - Tema: perfume bottle, lab equipment, sau "M" stylish

2. Copiaza fisierul "icon.png" in acest director:
   app/src/main/resources/images/icon.png

3. Recompileaza aplicatia:
   mvn clean install

4. Iconita va aparea:
   - In colțul ferestrei aplicației
   - In taskbar Windows
   - In Alt+Tab
   - Pe executabilul final (.exe)

==========================================
ALTERNATIVE:
- icon.ico (format Windows) - pentru compatibilitate
- icon@2x.png (512x512) - pentru rezoluții mari
==========================================

Aplicatia va rula normal chiar daca iconita nu este prezenta.












