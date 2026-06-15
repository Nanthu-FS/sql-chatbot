import { initLlama, LlamaContext } from 'llama.rn';
import { modelPath, GEMMA_MODELS } from './modelManager';

let _ctx: LlamaContext | null = null;

export async function getLlamaContext(): Promise<LlamaContext> {
  if (_ctx) return _ctx;
  _ctx = await initLlama({
    model: modelPath(GEMMA_MODELS[0].name),
    mmproj: modelPath(GEMMA_MODELS[1].name),
    n_ctx: 4096,
    n_batch: 512,
    use_mlock: true,
    n_threads: 4,
  });
  return _ctx;
}

export async function releaseContext() {
  if (_ctx) {
    await _ctx.release();
    _ctx = null;
  }
}

interface CompletionMessage {
  role: 'system' | 'user' | 'assistant';
  content: string | Array<{ type: 'text'; text: string } | { type: 'image_url'; image_url: { url: string } }>;
}

async function chat(messages: CompletionMessage[], temperature = 0.1): Promise<string> {
  const ctx = await getLlamaContext();
  const result = await ctx.completion({
    messages,
    n_predict: 1024,
    temperature,
    stop: ['</s>', '<end_of_turn>'],
    response_format: { type: 'json_object' },
  });
  return result.text.trim();
}

/**
 * Run a vision + text prompt and parse the JSON response.
 * imageUri is a local file path (file://...).
 */
export async function visionJSON<T>(system: string, prompt: string, imageUri: string): Promise<T> {
  const raw = await chat([
    { role: 'system', content: system },
    {
      role: 'user',
      content: [
        { type: 'image_url', image_url: { url: imageUri } },
        { type: 'text', text: prompt },
      ],
    },
  ]);
  return parseJSON<T>(raw);
}

/**
 * Run a text-only prompt and parse the JSON response.
 */
export async function textJSON<T>(system: string, prompt: string): Promise<T> {
  const raw = await chat([
    { role: 'system', content: system },
    { role: 'user', content: prompt },
  ]);
  return parseJSON<T>(raw);
}

function parseJSON<T>(raw: string): T {
  try {
    return JSON.parse(raw) as T;
  } catch {
    const match = raw.match(/\{[\s\S]*\}/);
    if (match) return JSON.parse(match[0]) as T;
    throw new Error(`Model did not return valid JSON. Got: ${raw.slice(0, 200)}`);
  }
}
