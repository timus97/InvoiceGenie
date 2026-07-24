/**
 * Browser-side OCR for cheque images using Tesseract.js.
 * PDFs are handled server-side (PDFBox); images are OCR'd here then parsed by the API.
 */

export type LocalOcrBlock = { sourceName: string; text: string };

const IMAGE_EXT = /\.(png|jpe?g|webp|gif|tif{1,2}|bmp)$/i;

export function isImageFile(file: File): boolean {
  return file.type.startsWith("image/") || IMAGE_EXT.test(file.name);
}

export function isPdfFile(file: File): boolean {
  return file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
}

export async function ocrImageFile(
  file: File,
  onProgress?: (pct: number, status: string) => void,
): Promise<LocalOcrBlock> {
  const { createWorker } = await import("tesseract.js");
  const worker = await createWorker("eng", 1, {
    logger: (m) => {
      if (m.status === "recognizing text" && typeof m.progress === "number") {
        onProgress?.(Math.round(m.progress * 100), m.status);
      }
    },
  });
  try {
    const result = await worker.recognize(file);
    return { sourceName: file.name, text: result.data.text || "" };
  } finally {
    await worker.terminate();
  }
}

export async function ocrImageFiles(
  files: File[],
  onFileProgress?: (fileName: string, pct: number) => void,
): Promise<LocalOcrBlock[]> {
  const blocks: LocalOcrBlock[] = [];
  for (const file of files) {
    if (!isImageFile(file)) continue;
    const block = await ocrImageFile(file, (pct) =>
      onFileProgress?.(file.name, pct),
    );
    blocks.push(block);
  }
  return blocks;
}