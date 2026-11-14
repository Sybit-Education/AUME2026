// typst compile coreteam.typ Artefakte-Core-Teams.pdf
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
#text(fill: subtitle, size: 15pt)[Sprint 0 (Iteration 0) · Digitalisierung der Sauberkeitskontrollen]
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
  [Iteration 0: 20.10.2026 – 03.11.2026],
  table.cell(x: 0, y: 5)[#text(weight: "bold")[Stand]],
  [Arbeitsfassung vom 14.11.2026],
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

#v(18pt)
#outline(
  title: text(size: 17pt, weight: "bold", fill: navy)[Inhaltsverzeichnis],
  depth: 3,
  indent: auto,
)

#let dir-of(path) = {
  let parts = path.split("/")
  if parts.len() <= 1 { "" } else { parts.slice(0, -1).join("/") + "/" }
}

#let include-md(path) = {
  let dir = dir-of(path)
  pagebreak(weak: true)
  cmarker.render(
    read(path),
    h1-level: 2,
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

#for path in (
  "architektur/Seezeit-Architektur.md",
  "projekt/prozessmodell/Prozessmodell.md",
  "anforderungen/qualitaet/qualitaetszenarien-definition-of-ready-done.md",
  "projekt/rollen_arbeitsvereinbarungen/README.md",
) {
  include-md(path)
}
