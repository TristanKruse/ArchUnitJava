# GitHub Pages

ArchUnitJava publishes its product guide and generated Java API reference at
<https://tristankruse.github.io/ArchUnitJava/>.

The landing page is maintained in `docs/site/`. The API reference is generated from the public Java
source with the Maven Javadoc plugin. `scripts/build-pages.ps1` combines both into `target/pages`,
adds the files required for static GitHub Pages hosting, and refuses to write outside that bounded
output directory. Generated documentation is intentionally kept out of source control.

Build the exact deployment artifact locally with JDK 25:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Prelease-candidate -DskipTests javadoc:javadoc
.\scripts\build-pages.ps1
```

On Linux or macOS, run the Maven wrapper as `./mvnw`; the Pages assembly script still requires
PowerShell (`pwsh`). Serve `target/pages` with any static file server to inspect root-relative and
API-reference links in a browser.

Every push to `main` and every manual documentation run builds the site in
`.github/workflows/docs.yml`. Successful builds from `main` are deployed with GitHub's official
Pages actions. Pull requests validate documentation through the main CI release-candidate build.
