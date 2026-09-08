@ocr-quality
Feature: OCR quality detection on scanned pages (OCR-QUALITY-3)
  As a book producer iterating on OCR quality
  I want doubtful pages automatically located in the OCR issue report
  So that ghost images, lost table cells and uncertain structure are caught without re-reading the whole book

  @ghost-image
  Scenario: A ghost image reference is flagged as IMAGE_MISSING
    Given ocr-quality a scans directory with page "010.adoc" containing
      """
      == 1.0.2.1 Diagramme
      image::page-010-diagram.png[]
      Poursuivons l'analyse du referentiel avec assez de contenu.
      """
    When ocr-quality the OCR failures are detected
    Then ocr-quality the issues are
      | reason        | detail                 |
      | IMAGE_MISSING | page-010-diagram.png   |

  @materialised-image
  Scenario: An image reference with an existing file is not flagged
    Given ocr-quality a scans directory with page "011.adoc" containing
      """
      == 1.0.2.2 Photo
      image::page-011-photo.png[]
      Legende longue et claire du document numerise.
      """
    And ocr-quality the file "page-011-photo.png" exists in the scans directory
    When ocr-quality the OCR failures are detected
    Then ocr-quality no issue is reported

  @inline-image
  Scenario: An inline image macro reference is flagged as IMAGE_MISSING
    Given ocr-quality a scans directory with page "061.adoc" containing
      """
      == 1.2.8 Schema heuristique
      image:cerveau_gauche_vs_cerveau_droit.jpg[Brain mapping]
      Le texte poursuit son analyse pedagogique avec assez de contenu.
      """
    When ocr-quality the OCR failures are detected
    Then ocr-quality the issues are
      | reason        | detail                              |
      | IMAGE_MISSING | cerveau_gauche_vs_cerveau_droit.jpg |

  @linearised-table
  Scenario: A linearised table row is flagged as TABLE_SUSPECT
    Given ocr-quality a scans directory with page "020.adoc" containing
      """
      == 1.1.1 Tableau
      | Modele | Cible | Niveau |
      Le texte reprend ensuite sa narration normale avec suffisamment de contenu.
      """
    When ocr-quality the OCR failures are detected
    Then ocr-quality the issues are
      | reason        | detail                    |
      | TABLE_SUSPECT | \| Modele \| Cible \| Niveau \| |

  @invalid-part
  Scenario: An "invalid part" marker is flagged as STRUCT_SUSPECT
    Given ocr-quality a scans directory with page "030.adoc" containing
      """
      == 1.2.1 Partie
      invalid part — le modele ne peut pas determiner la structure
      Le contenu continue malgre cette erreur structurelle evidente.
      """
    When ocr-quality the OCR failures are detected
    Then ocr-quality the issues are
      | reason         | detail        |
      | STRUCT_SUSPECT | invalid part  |

  @out-of-sequence
  Scenario: An "out of sequence" marker is flagged as STRUCT_SUSPECT
    Given ocr-quality a scans directory with page "031.adoc" containing
      """
      == 1.2.2 Suite
      out of sequence — la page precedente semble manquee
      Le reste du texte est intact et assez long pour la lecture humaine.
      """
    When ocr-quality the OCR failures are detected
    Then ocr-quality the issues are
      | reason         | detail           |
      | STRUCT_SUSPECT | out of sequence  |

  @multi-issue
  Scenario: One page can carry several distinct issues without losing any signal
    Given ocr-quality a scans directory with page "040.adoc" containing
      """
      == 1.3.1 Multiple
      invalid part — structure incertaine
      | A | B | C |
      image::ghost-040.png[]
      Un texte final assez long pour ne pas declencher TOO_SHORT.
      """
    When ocr-quality the OCR failures are detected
    Then ocr-quality the issues are
      | reason         | detail        |
      | STRUCT_SUSPECT | invalid part  |
      | TABLE_SUSPECT  | \| A \| B \| C \| |
      | IMAGE_MISSING  | ghost-040.png |

  @report-shape
  Scenario: The issue report carries detail evidence and keeps whole-page issues unchanged
    Given ocr-quality a scans directory with page "073.adoc" containing "[ILLISIBLE]"
    And ocr-quality a scans directory with page "050.adoc" containing
      """
      == 1.4.1 Mixte
      | X | Y |
      image::ghost-050.png[]
      Le corps reste suffisamment long pour la lecture.
      """
    When ocr-quality the OCR failures are detected and written to a report
    Then ocr-quality the ILLISIBLE issue carries no detail field
    And ocr-quality the IMAGE_MISSING issue carries detail "ghost-050.png"