# World Time Earth texture

## Asset used in the APK

- **Source:** [NASA Earth Observatory — Blue Marble Next Generation with Topography](https://science.nasa.gov/earth/earth-observatory/blue-marble-next-generation/base-topography/), May 2004 global composite.
- **Credit:** NASA Earth Observatory / Blue Marble Next Generation. NASA imagery usage follows the NASA Media Usage Guidelines; the credit is retained here with the asset.
- **Original downloaded dataset:** `world.topo.2004-05.png`, equirectangular 5400×2700 px, cloud-free base map with land topography.
- **APK texture:** `app/src/main/res/drawable-nodpi/world_time_earth.jpg`, equirectangular 1024×512 px, JPEG quality 90, 84,597 bytes.
- **Processing:** downscaled with Lanczos resampling and vertically flipped once for Android bitmap row order versus the existing OpenGL UV convention. This preserves the existing North/South city coordinates; no mesh or latitude/longitude data was changed. No clouds, labels, city data, SPB Shell imagery, APK extraction, or coordinate edits were introduced.

## Runtime lifecycle

`WorldTimeScene` declares a logical `WidgetTextureRef`; `ShellRenderer` and `TextureManager` own the GL texture, cache it by key, rebuild it after EGL context recreation, and release it during renderer release. If Android cannot decode the resource, a one-pixel project-color bitmap avoids a crash; the old procedural globe is no longer the normal visual.

## Clouds

`world_time_clouds.png` is a sparse transparent cloud mask generated locally at 512×256 px. It is loaded once by the same renderer texture cache, keeps the NASA base map cloud-free, and can be replaced by a WeatherScene layer later.
