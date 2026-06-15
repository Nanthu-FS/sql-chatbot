const BASE = 'https://rxnav.nlm.nih.gov/REST';

export async function normalizeDrug(name: string): Promise<{ rxcui: string | null; name: string | null }> {
  try {
    const res = await fetch(`${BASE}/rxcui.json?name=${encodeURIComponent(name)}&search=2`);
    if (!res.ok) return { rxcui: null, name: null };
    const json = await res.json();
    const rxcui: string | undefined = json?.idGroup?.rxnormId?.[0];
    if (!rxcui) return { rxcui: null, name: null };

    const propRes = await fetch(`${BASE}/rxcui/${rxcui}/property.json?propName=RxNorm+Name`);
    if (!propRes.ok) return { rxcui, name: null };
    const propJson = await propRes.json();
    const canonical: string | undefined = propJson?.propConceptGroup?.propConcept?.[0]?.propValue;
    return { rxcui, name: canonical ?? null };
  } catch {
    return { rxcui: null, name: null };
  }
}
