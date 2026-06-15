import { visionJSON } from './llm';
import { normalizeDrug } from './rxnorm';
import { fetchLabel } from './openfda';
import type { Identification, VisionRead } from './types';

const SYSTEM = `You are a pharmacology vision assistant. You examine a photo of a single
pill or tablet and report only what you can actually observe, plus a calibrated best guess
of the medication. You are NOT a doctor and must never state a diagnosis or dosing advice.
Be conservative: if the imprint is unreadable, say so and lower your confidence.`;

const PROMPT = `Look at this pill image. Return ONE JSON object with EXACTLY these keys:
{
  "name": string|null,
  "imprint": string|null,
  "shape": string|null,
  "color": string|null,
  "strength": string|null,
  "dosage_form": string|null,
  "confidence": number,
  "reasoning": string|null,
  "candidates": string[]
}
Only describe what is visible. Do not output any text outside the JSON object.`;

export async function identifyPill(imageUri: string): Promise<Identification> {
  let vision = await visionJSON<VisionRead>(SYSTEM, PROMPT, imageUri);

  vision = {
    name: vision.name ?? null,
    imprint: vision.imprint ?? null,
    shape: vision.shape ?? null,
    color: vision.color ?? null,
    strength: vision.strength ?? null,
    dosage_form: vision.dosage_form ?? null,
    confidence: clamp(vision.confidence),
    reasoning: vision.reasoning ?? null,
    candidates: Array.isArray(vision.candidates) ? vision.candidates.slice(0, 3) : [],
  };

  let rxcui: string | null = null;
  let canonical_name: string | null = null;
  let label = null;

  if (vision.name) {
    const [rx, lbl] = await Promise.all([
      normalizeDrug(vision.name),
      fetchLabel(vision.name),
    ]);
    rxcui = rx.rxcui;
    canonical_name = rx.name;
    label = lbl;
  }

  return { vision, rxcui, canonical_name, label };
}

function clamp(n: unknown): number {
  const v = typeof n === 'number' ? n : Number(n);
  if (!Number.isFinite(v)) return 0;
  return Math.max(0, Math.min(100, Math.round(v)));
}
