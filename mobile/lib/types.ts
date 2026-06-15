export type Severity = 'minor' | 'moderate' | 'major';

export interface VisionRead {
  name: string | null;
  imprint: string | null;
  shape: string | null;
  color: string | null;
  strength: string | null;
  dosage_form: string | null;
  confidence: number;
  reasoning: string | null;
  candidates: string[];
}

export interface LabelData {
  brand_name: string | null;
  generic_name: string | null;
  purpose: string | null;
  warnings: string | null;
  drug_interactions: string | null;
  dosage_and_administration: string | null;
  source: 'openfda';
}

export interface Identification {
  vision: VisionRead;
  rxcui: string | null;
  canonical_name: string | null;
  label: LabelData | null;
}

export interface Medication {
  id: string;
  name: string;
  brand_name: string | null;
  rxcui: string | null;
  strength: string | null;
  dosage_form: string | null;
  imprint: string | null;
  shape: string | null;
  color: string | null;
  ndc: string | null;
  confidence: number | null;
  notes: string | null;
  image_path: string | null;
  label_data: LabelData | null;
  created_at: string;
}

export interface InteractionFinding {
  drug_a: string;
  drug_b: string;
  severity: Severity;
  summary: string;
  source_drug: string;
}

export interface ModelFile {
  name: string;
  url: string;
  sizeBytes: number;
}

export const GEMMA_MODELS: ModelFile[] = [
  {
    name: 'gemma-3-4b-it-Q4_K_M.gguf',
    url: 'https://huggingface.co/bartowski/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf',
    sizeBytes: 2_600_000_000,
  },
  {
    name: 'mmproj-gemma-3-4b-it-f16.gguf',
    url: 'https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/mmproj-gemma-3-4b-it-f16.gguf',
    sizeBytes: 620_000_000,
  },
];
