# Acrobat Free — All-in-One Professional PDF Reader & Document Studio

A comprehensive, 100% free Android PDF Reader inspired by Adobe Acrobat Pro, featuring professional document viewing, full markup and annotation tools, digital signature capture, document editing tools (image-to-PDF, PDF merge, page management), and a next-generation AI Document Assistant with deep reasoning.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The user confirmed the following core design choices during Phase 1:
> - **Feature Scope**: Full Adobe Acrobat Pro suite — high-fidelity rendering, markup tools, digital signatures, document manipulation tools, and an AI Document Assistant.
> - **Document Access**: Pre-loaded rich sample PDFs (business contract, technical guide, research brief, invoice) alongside the native Android Storage Access Framework (SAF) document picker.
> - **Visual Theme**: *Acrobat Crimson* — high-contrast executive dark interface (`#121214` / `#1E1E24`) with Adobe's signature Crimson accent (`#E11D48`), paper-white document rendering, and dark/sepia reading mode filters.
> - **AI Intelligence**: Configured with `gemini-4-argon` and `thinkingLevel = "HIGH"` for in-depth document comprehension, synthesis, and question answering.

---

### 1. Overview & Core Concept

* **What It Does**: A complete, uncompromised PDF workspace for Android that provides all premium PDF utilities for free without subscriptions or watermarks.
* **Target Audience**: Students, professionals, legal practitioners, and everyday users who need to read, annotate, sign, merge, and interrogate PDF documents on mobile.
* **Key Value**: Delivers desktop-grade PDF capabilities—continuous reading, multi-color ink markup, digital signature stamping, document creation, and conversational AI document analysis—directly on device.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Library & Dashboard Flow**:
   - Launches into the Acrobat Dashboard showcasing "Recent Documents", "Quick Tools" carousel, and "Sample Library".
   - Users can tap a sample document (e.g. *Mutual Non-Disclosure Agreement*, *Technical Architecture Whitepaper*, *Standard Invoice*) or tap "Open PDF" to pick any file from storage.
2. **Reader & Navigation Flow**:
   - High-fidelity PDF rendering with fluid zoom/pan, vertical continuous scroll or horizontal page flipping.
   - Page thumbnail grid drawer for instant page hopping.
   - Quick display toggles: Day (white paper), Night (inverted dark paper for low light), and Sepia (warm paper tone).
3. **Annotation & Markup Studio**:
   - Floating Adobe-style annotation bar:
     - **Highlighter**: Semi-transparent yellow, green, pink, or cyan ink.
     - **Freehand Pen**: Variable stroke width and color palette with smooth bezier curve interpolation.
     - **Sticky Notes**: Tap anywhere on a page to attach a comment bubble with timestamp.
     - **Stamp Tool**: One-tap stamps ("APPROVED", "CONFIDENTIAL", "DRAFT", "URGENT", "VOID").
     - **Eraser & Undo/Redo**: Effortless correction of ink marks.
4. **Fill & Sign Studio**:
   - Draw handwritten signatures with touch or stylus on a smooth signature pad.
   - Save signatures to a reusable personal signature vault.
   - Place, scale, and move signature stamps onto contract lines and export the signed document.
5. **PDF Utility Tools**:
   - **Image to PDF**: Select device photos or receipts and convert to a clean multi-page PDF.
   - **Merge PDFs**: Combine multiple PDF documents into a single consolidated file.
   - **Page Manager**: View all pages in a grid, rotate pages 90°, and delete or reorder pages.
6. **AI Document Assistant (with High Thinking)**:
   - Slide-over Acrobat AI panel.
   - One-tap actions: "Executive Summary", "Key Action Items", "Explain Complex Terms", "Generate Study Flashcards".
   - Freeform chat prompt with document context, displaying the model's analytical thought process alongside the structured response.

#### Visual Theme & Styling Tokens
* **Aesthetic Direction**: Sleek, professional executive utility with crisp typography and tactile toolbar buttons.
* **Color Palette (Acrobat Crimson)**:
  - Background: `#111115` (Deep Charcoal Black)
  - Surface & Cards: `#1A1A22` (Card Surface) / `#262630` (Elevated Toolbars)
  - Primary Accent: `#E11D48` (Acrobat Crimson Red)
  - Primary Accent Light: `#FB7185`
  - Success / Approved: `#10B981` (Emerald)
  - Warning / Note: `#F59E0B` (Amber)
  - Page Canvas: `#FFFFFF` (Document Paper) / `#18181B` (Night Mode Inversion)
  - Text Primary: `#F8FAFC`
  - Text Secondary: `#94A3B8`

---

### 3. Key Product Decisions & Trade-Offs

* **Rendering Engine**:
  - *Chosen Approach*: Android's built-in `android.graphics.pdf.PdfRenderer` with bitmap tile caching and coroutine background dispatchers, coupled with `android.graphics.pdf.PdfDocument` for generating, exporting, and merging.
  - *Why*: Guaranteed 100% native stability, zero bloated external C++ binary dependencies, instant page rendering, and full offline fidelity.
* **Markup Storage & Rendering**:
  - *Chosen Approach*: Layered Canvas vector overlay with Room database persistence for editable annotations, plus bitmap rasterization during "Export Signed/Annotated PDF".
  - *Why*: Allows users to re-edit, delete, or move annotations after saving, while also allowing clean export to standalone PDF files for sharing.
* **AI Model Selection**:
  - *Chosen Approach*: Direct REST integration using `gemini-4-argon` with `thinkingLevel = "HIGH"` via secure `BuildConfig.GEMINI_API_KEY`.
  - *Why*: Provides the deep comprehension and logical reasoning needed for dense contracts and academic papers, adhering strictly to the user's requirement.

---

### 4. Technical Architecture & Component Hierarchy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (Compose Edge-to-Edge)             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    ▼                               ▼                               ▼
┌────────────────────────┐  ┌────────────────────────┐  ┌────────────────────────┐
│    Library / Home      │  │     Reader Screen      │  │    PDF Tools Hub       │
│  - Recent PDFs         │  │  - PdfRenderer Canvas  │  │  - Image to PDF        │
│  - Preloaded Samples   │  │  - Zoom/Pan Container  │  │  - Merge Documents     │
│  - System File Picker  │  │  - Day/Night/Sepia     │  │  - Page Manager/Rotate │
└────────────────────────┘  └───────────┬────────────┘  └────────────────────────┘
                                        │
           ┌────────────────────────────┼────────────────────────────┐
           ▼                            ▼                            ▼
┌─────────────────────┐      ┌─────────────────────┐      ┌─────────────────────┐
│  Annotation Overlay │      │  Signature Vault    │      │ Acrobat AI Panel    │
│  - Freehand Pen     │      │  - Touch Signature  │      │  - gemini-4-argon   │
│  - Highlighter      │      │  - Stamp Placement  │      │  - ThinkingLevel.HIGH│
│  - Sticky Notes     │      │  - Flatten to PDF   │      │  - Summary & Q&A    │
└─────────────────────┘      └─────────────────────┘      └─────────────────────┘
           │                            │                            │
           ▼                            ▼                            ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Data Layer & Native PDF Services                     │
│  - PdfRendererManager (Native Android PdfRenderer)                     │
│  - PdfExportEngine (android.graphics.pdf.PdfDocument)                  │
│  - AppDatabase (Room: Documents, Annotations, Signatures)              │
│  - GeminiRestRepository (Retrofit + Kotlinx Serialization)             │
└────────────────────────────────────────────────────────────────────────┘
```

#### Key Architecture Components
1. **`PdfRendererManager`**: Thread-safe manager handling file descriptors, page dimension calculation, bitmap caching, and async page rendering.
2. **`PdfExportEngine`**: Handles combining original PDF pages, ink strokes, text notes, and signatures into a new distributable PDF file using `android.graphics.pdf.PdfDocument`.
3. **`AppDatabase` & DAOs**:
   - `DocumentEntity`: Tracks file URI/path, title, page count, last read page, reading mode, and favorite status.
   - `AnnotationEntity`: Stores strokes, highlighters, sticky notes, and text stamps with normalized page coordinates.
   - `SignatureEntity`: Stores saved handwritten vector signatures for rapid re-use.
4. **`GeminiRestService`**: Configured with `gemini-4-argon`, `ThinkingConfig(thinkingLevel = "high")`, and structured prompt formats to analyze document excerpts and answer questions.
5. **Preloaded Sample Documents**: Generated real PDF files bundled in cache/assets containing realistic sample content (Contract agreement, Tech Whitepaper, Financial Invoice) to ensure the app is immediately usable out of the box.
