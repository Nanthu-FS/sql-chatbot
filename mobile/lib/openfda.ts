import type { LabelData } from './types';

const BASE = 'https://api.fda.gov/drug/label.json';

interface FdaResult {
  brand_name?: string[];
  generic_name?: string[];
  purpose?: string[];
  warnings?: string[];
  drug_interactions?: string[];
  dosage_and_administration?: string[];
}

interface FdaResponse {
  results?: FdaResult[];
  error?: { message: string };
}

export async function fetchLabel(drugName: string): Promise<LabelData | null> {
  try {
    const q = encodeURIComponent(`openfda.generic_name:"${drugName}" OR openfda.brand_name:"${drugName}"`);
    const res = await fetch(`${BASE}?search=${q}&limit=1`);
    if (!res.ok) return null;
    const json: FdaResponse = await res.json();
    const r = json.results?.[0];
    if (!r) return null;
    return {
      brand_name: r.brand_name?.[0] ?? null,
      generic_name: r.generic_name?.[0] ?? null,
      purpose: r.purpose?.[0]?.slice(0, 800) ?? null,
      warnings: r.warnings?.[0]?.slice(0, 1200) ?? null,
      drug_interactions: r.drug_interactions?.[0]?.slice(0, 1200) ?? null,
      dosage_and_administration: r.dosage_and_administration?.[0]?.slice(0, 600) ?? null,
      source: 'openfda',
    };
  } catch {
    return null;
  }
}
