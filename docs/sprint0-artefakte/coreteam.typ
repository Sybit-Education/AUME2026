// typst compile --root .. coreteam.typ Artefakte_CoreTeam_Sprint0.pdf
#import "@preview/cmarker:0.1.10"

#let navy = rgb("#18344f")
#let teal = rgb("#1fa597")
#let line-gray = rgb("#c9d3dd")
#let label-fill = rgb("#eef2f6")
#let subtitle = rgb("#5a6877")

#set page(
  paper: "a4",
  margin: 1.7cm,
  numbering: "1",
  number-align: bottom + right,
)
#set text(lang: "de", font: "Liberation Sans", size: 11pt)
#set list(marker: ([•], [–]))
#set image(width: 100%, fit: "contain")

#show heading.where(level: 1): set text(size: 30pt, weight: "bold", fill: navy)
#show heading.where(level: 2): set text(size: 17pt, weight: "bold", fill: navy)
#show heading.where(level: 3): set text(size: 13pt, weight: "bold", fill: navy)
#show heading.where(level: 4): set text(size: 11pt, weight: "bold", fill: navy)
#show heading.where(level: 5): set text(size: 11pt, weight: "bold", fill: navy)

#text(fill: teal, weight: "bold", size: 11pt)[AUME · WINTERSEMESTER 2026/27]
#v(10pt)
#heading(level: 1, outlined: false)[Artefakte der Core Teams]
#v(2pt)
#text(fill: subtitle, size: 15pt)[Sprint 0 · Digitalisierung der Sauberkeitskontrollen]
#v(16pt)

#table(
  columns: (3.4cm, 1fr),
  stroke: 0.5pt + line-gray,
  inset: (x: 6pt, y: 5pt),
  fill: (x, _) => if x == 0 { label-fill },
  table.cell(x: 0)[#text(weight: "bold")[Praxispartner]],
  [Studierendenwerk Seezeit (Kunde) · Sybit (Technologieberatung)],
  table.cell(x: 0, y: 1)[#text(weight: "bold")[Kurs]],
  [Agile Vorgehensmodelle und Mobile Kommunikation (AUME), Master, Fakultät Informatik, HTWG Konstanz],
  table.cell(x: 0, y: 2)[#text(weight: "bold")[Betreuung]],
  [Prof. Dr. R. Mueller · Prof. Dr. R. Schimkat],
  table.cell(x: 0, y: 3)[#text(weight: "bold")[Team]],
  [Core Team, vier Teilgruppen: Architektur · Prozessmodell · Qualität · Rollen und Arbeitsvereinbarung],
  table.cell(x: 0, y: 4)[#text(weight: "bold")[Zeitraum]],
  [20.10.2026 – 03.11.2026],
  table.cell(x: 0, y: 5)[#text(weight: "bold")[Stand]],
  [Abgabe zum 16.11.2026],
)

#v(14pt)
#text(weight: "bold", fill: navy, size: 13pt)[Mitglieder der Teilgruppen]
#v(6pt)
#table(
  columns: (6.2cm, 1fr),
  stroke: 0.5pt + line-gray,
  inset: (x: 6pt, y: 5pt),
  table.header(
    table.cell(fill: navy)[#text(fill: white, weight: "bold")[Teilgruppe]],
    table.cell(fill: navy)[#text(fill: white, weight: "bold")[Mitglieder]],
  ),
  [Architektur], [Jonas Wunsch · Marian Keller],
  [Qualität], [Eduard Vilgauk · Mika Luu · Fabian Sichert],
  [Prozessmodell], [Evelyn Klein · Sophie Weiß],
  [Rollen und Arbeitsvereinbarung], [Jonas Mayer · Benedict Benthien · Tim Lanzinger],
)

#pagebreak()
#outline(
  title: text(size: 17pt, weight: "bold", fill: navy)[Inhaltsverzeichnis],
  depth: 3,
  indent: auto,
)

#let dir-of(path) = {
  let parts = path.split("/")
  if parts.len() <= 1 { "" } else { parts.slice(0, -1).join("/") + "/" }
}

#let include-md(path, h1-level: 2) = {
  let dir = dir-of(path)
  pagebreak(weak: true)
  cmarker.render(
    read(path),
    h1-level: h1-level,
    label-prefix: path.replace(".md", "").replace("/", "-") + "-",
    scope: (
      image: (source, ..args) => if source.starts-with("http://") or source.starts-with("https://") {
        let alt = args.named().at("alt", default: "Abbildung")
        text(style: "italic", fill: subtitle, size: 10pt)[#alt (externe Abbildung, nicht eingebettet)]
      } else {
        image(dir + source, ..args)
      },
    ),
  )
}

#show table: set text(size: 9pt)
#set table(
  inset: (x: 5pt, y: 4pt),
  stroke: 0.5pt + line-gray,
  fill: (_, y) => if y == 0 { navy },
)
#show table.cell.where(y: 0): set text(fill: white, weight: "bold", size: 9pt)
#show table.cell.where(x: 0): set text(weight: "bold")

#for (path, level) in (
  ("../architektur/Seezeit-Architektur.md", 2),
  ("../architektur/adr/README.md", 3),
  ("../architektur/adr/0001-record-decisions-as-markdown-in-git.md", 3),
  ("../architektur/adr/0002-deploy-the-application-using-docker-compose.md", 3),
  ("../architektur/adr/0003-structure-the-backend-as-a-modular-monolith.md", 3),
  ("../architektur/adr/0004-provide-a-responsive-browser-frontend.md", 3),
  ("../architektur/adr/0005-use-postgresql-and-private-attachment-storage.md", 3),
  ("../architektur/adr/0006-centralize-authentication-and-enforce-scoped-authorization.md", 3),
  ("../architektur/adr/0007-version-standards-and-preserve-published-inspection-history.md", 3),
  ("../architektur/adr/0008-run-background-work-through-a-transactional-outbox.md", 3),
  ("../architektur/adr/0009-harden-compose-operations-and-verify-recovery.md", 3),
  ("../architektur/adr/0010-track-cra-scope-and-product-security-evidence.md", 3),
  ("../architektur/adr/0011-preserve-inspection-drafts-during-network-loss.md", 3),
  ("../projekt/prozessmodell/Prozessmodell.md", 2),
  ("../anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md", 2),
  ("../projekt/rollen_arbeitsvereinbarungen/README.md", 2),
) {
  include-md(path, h1-level: level)
}
