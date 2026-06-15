import { textJSON } from './llm';
import { fetchLabel } from './openfda';
import type { InteractionFinding, LabelData, Medication } from './types';

interface InMed { name: string; label_data?: LabelData | null }

const SYSTEM = `You are a clinical-interaction summarizer. You are given a patient's medication
list and, for some drugs, the "Drug Interactions" / "Warnings" text taken directly from their
official FDA label. Your ONLY job is to report interactions that are SUPPORTED by the provided
label text. Do not invent interactions from general knowledge. Assign severity conservatively.`;

function buildPrompt(meds: InMed[]): string {
  const list = meds.map((m, i) => `${i + 1}. ${m.name}`).join('\n');
  const labels = meds
    .filter((m) => m.label_data?.drug_interactions || m.label_data?.warnings)
    .map(
      (m) =>
        `### ${m.name}\nDrug Interactions: ${m.label_data?.drug_interactions ?? 'n/a'}\nWarnings: ${m.label_data?.warnings ?? 'n/a'}`,
    )
    .join('\n\n');

  return `MEDICATION LIST:\n${list}\n\nFDA LABEL TEXT:\n${labels || '(no label text available)'}\n
Return ONE JSON object: { "findings": Finding[] } where each Finding is:
{
  "drug_a": string,
  "drug_b": string,
  "severity": "minor"|"moderate"|"major",
  "summary": string,
  "source_drug": string
}
Only include pairs that are BOTH in the medication list AND supported by the label text above.
If none are supported, return { "findings": [] }. No text outside the JSON object.`;
}

export async function checkInteractions(
  meds: Medication[],
): Promise<{ findings: InteractionFinding[]; note: string | null }> {
  if (meds.length < 2) {
    return { findings: [], note: 'Add at least two medications to check interactions.' };
  }

  const enriched: InMed[] = await Promise.all(
    meds.map(async (m) => {
      if (m.label_data?.drug_interactions || m.label_data?.warnings) {
        return { name: m.name, label_data: m.label_data };
      }
      const label = await fetchLabel(m.name);
      return { name: m.name, label_data: label };
    }),
  );

  const haveText = enriched.some(
    (m) => m.label_data?.drug_interactions || m.label_data?.warnings,
  );
  if (!haveText) {
    return {
      findings: [],
      note: 'No FDA label interaction text was available for these medications.',
    };
  }

  const parsed = await textJSON<{ findings?: InteractionFinding[] }>(
    SYSTEM,
    buildPrompt(enriched),
  );

  const names = new Set(enriched.map((m) => m.name.toLowerCase()));
  const findings = (parsed.findings ?? [])
    .filter(
      (f) =>
        f?.drug_a &&
        f?.drug_b &&
        names.has(f.drug_a.toLowerCase()) &&
        names.has(f.drug_b.toLowerCase()) &&
        ['minor', 'moderate', 'major'].includes(f.severity),
    )
    .slice(0, 50);

  return { findings, note: null };
}
