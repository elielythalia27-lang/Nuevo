import subprocess

# Generate a high-resolution SVG of the combined launcher icon
svg_content = """<?xml version="1.0" encoding="utf-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
  <defs>
    <linearGradient id="groundShadow" x1="54" y1="74" x2="54" y2="80" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#004D40" stop-opacity="0.2"/>
      <stop offset="100%" stop-color="#000000" stop-opacity="0"/>
    </linearGradient>
    <linearGradient id="spineFront" x1="30" y1="28" x2="41" y2="76" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#00BFA5"/>
      <stop offset="45%" stop-color="#00897B"/>
      <stop offset="100%" stop-color="#00695C"/>
    </linearGradient>
    <linearGradient id="loopBody" x1="40" y1="28" x2="78" y2="77" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#1DE9B6"/>
      <stop offset="25%" stop-color="#00BFA5"/>
      <stop offset="65%" stop-color="#00897B"/>
      <stop offset="100%" stop-color="#004D40"/>
    </linearGradient>
    <linearGradient id="arrowFront" x1="52" y1="68.5" x2="64" y2="78.5" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#00E676"/>
      <stop offset="40%" stop-color="#00BFA5"/>
      <stop offset="100%" stop-color="#00796B"/>
    </linearGradient>
    <linearGradient id="playShadow" x1="46.5" y1="42" x2="68.5" y2="67" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#000000" stop-opacity="0.32"/>
      <stop offset="100%" stop-color="#000000" stop-opacity="0.16"/>
    </linearGradient>
    <linearGradient id="playBody" x1="46" y1="40.5" x2="68" y2="64" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#FF5252"/>
      <stop offset="30%" stop-color="#FF1744"/>
      <stop offset="75%" stop-color="#D50000"/>
      <stop offset="100%" stop-color="#8B0000"/>
    </linearGradient>
    <linearGradient id="playGloss" x1="47.5" y1="41.5" x2="66" y2="52.5" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.5"/>
      <stop offset="100%" stop-color="#FFFFFF" stop-opacity="0"/>
    </linearGradient>
  </defs>

  <!-- Background Layer (Pure White) -->
  <rect width="108" height="108" fill="#FFFFFF"/>

  <!-- 3D Soft Ambient Ground Shadow -->
  <path d="M32,77 C32,74 76,74 76,77 C76,80 32,80 32,77 Z" fill="url(#groundShadow)"/>

  <!-- 3D Isometric Teal Anchor Spine - Back Depth Layer -->
  <path d="M31,34.5 C31,31.5 33.5,29.5 36.5,29.5 L42.5,29.5 L42.5,77.5 L36.5,77.5 C33.5,77.5 31,75.5 31,72.5 Z" fill="#004D40"/>

  <!-- 3D Isometric Teal Anchor Spine - Front Face -->
  <path d="M30,33 C30,30 32.5,28 35.5,28 L40.5,28 L40.5,76 L35.5,76 C32.5,76 30,74 30,71 Z" fill="url(#spineFront)"/>

  <!-- Spine 3D Top Highlight Chamfer -->
  <path d="M31,33 C31,31 33,29 35.5,29 L40.5,29 L40.5,31.5 L35.5,31.5 C33.5,31.5 32,32.5 31.5,34 Z" fill="#E0F2F1" fill-opacity="0.5"/>

  <!-- 3D Teal Download/Media Loop - Depth Shadow Layer -->
  <path d="M41,30.5 C57,30.5 74.5,35.5 78.5,49.5 C82.5,62.5 75.5,73.5 64.5,78.5 C56.5,81.5 48,79.5 42,79.5 L44,71.5 C48,71.5 54,73.5 60.5,70.5 C68.5,66.5 72.5,58.5 69.5,49.5 C66.5,40.5 53.5,36.5 42,37.5 Z" fill="#00382E"/>

  <!-- 3D Teal Download/Media Loop - Main Body -->
  <path d="M40,28 C56,28 73.5,33 77.5,47 C81.5,60 74.5,71 63.5,76 C55.5,79 47,77 41,77 L43,69 C47,69 53,71 59.5,68 C67.5,64 71.5,56 68.5,47 C65.5,38 52.5,34 41,35 Z" fill="url(#loopBody)"/>

  <!-- 3D Loop Top Specular Edge Highlight -->
  <path d="M41,28 C55,28 71,32.5 75.5,45 C73.5,35 60,30.5 41,30.5 Z" fill="#FFFFFF" fill-opacity="0.55"/>

  <!-- 3D Download Arrow Pointer - Depth Edge -->
  <path d="M56,70 L65.5,78.5 L53,80.5 L55,71 Z" fill="#00332A"/>

  <!-- 3D Download Arrow Pointer - Front -->
  <path d="M55,68.5 L64,76.5 L52,78.5 L54,69.5 Z" fill="url(#arrowFront)"/>

  <!-- 3D Ruby Red Play Button Jewel - Soft Drop Shadow -->
  <path d="M48.5,43.5 C49.5,42.5 51,42.5 52,43.5 L67.5,52.5 C69,53.5 69,56 67.5,57 L52,66 C51,67 49.5,67 48.5,66 C47.5,65 47,64 47,62.5 L47,47 C47,45.5 47.5,44.5 48.5,43.5 Z" fill="url(#playShadow)"/>

  <!-- 3D Ruby Red Play Button Jewel - 3D Bevel Body -->
  <path d="M47.5,41.5 C48.5,40.5 50,40.5 51,41.5 L66.5,50.5 C68,51.5 68,54 66.5,55 L51,64 C50,65 48.5,65 47.5,64 C46.5,63 46,62 46,60.5 L46,45 C46,43.5 46.5,42.5 47.5,41.5 Z" fill="url(#playBody)"/>

  <!-- 3D Ruby Red Top Gloss Facet -->
  <path d="M47.5,41.5 L66,52.5 L47.5,52.5 Z" fill="url(#playGloss)"/>

  <!-- 3D Specular Center Highlight Dot -->
  <path d="M49,44.5 C49,43.7 49.8,43.7 50.3,44.2 L56.5,48 C57,48.4 57,49 56.5,49.4 L50.3,53.2 C49.8,53.7 49,53.7 49,52.9 Z" fill="#FFFFFF" fill-opacity="0.25"/>
</svg>
"""

with open("/tmp/icon.svg", "w") as f:
    f.write(svg_content)

print("SVG generated successfully")
