/**
 * ThoughtDrop Web Studio - Dual AI Engine with Multi-Key Pool Rotation
 * - Google Gemini Native (Hydrogen & Flash tiers)
 * - Groq Cloud Llama 3 Failover
 * - Automatic Key Failover on Quota Exhaustion (429)
 * - Parity with ThoughtDrop Android App
 */

// Helper to get all Gemini keys
export function getGeminiKeys() {
  const poolStr = localStorage.getItem('thoughtdrop_gemini_pool');
  if (poolStr) {
    try {
      const pool = JSON.parse(poolStr);
      if (Array.isArray(pool) && pool.length > 0) return pool;
    } catch (_) {}
  }
  const single = localStorage.getItem('thoughtdrop_gemini_key');
  if (single) {
    return [{ id: 'gemini_primary', key: single, label: 'Primary Gemini Key', isExhausted: false }];
  }
  return [];
}

// Helper to get all Groq keys
export function getGroqKeys() {
  const poolStr = localStorage.getItem('thoughtdrop_groq_pool');
  if (poolStr) {
    try {
      const pool = JSON.parse(poolStr);
      if (Array.isArray(pool) && pool.length > 0) return pool;
    } catch (_) {}
  }
  const single = localStorage.getItem('thoughtdrop_groq_key');
  if (single) {
    return [{ id: 'groq_primary', key: single, label: 'Primary Groq Key', isExhausted: false }];
  }
  return [];
}

export function getSelectedModel() {
  return localStorage.getItem('thoughtdrop_selected_model') || 'gemini-4-hydrogen';
}

export function getSelectedEngine() {
  return localStorage.getItem('thoughtdrop_selected_engine') || 'auto';
}

export function markKeyExhausted(provider, keyId) {
  const storageKey = provider === 'groq' ? 'thoughtdrop_groq_pool' : 'thoughtdrop_gemini_pool';
  const pool = provider === 'groq' ? getGroqKeys() : getGeminiKeys();
  const updated = pool.map(item => item.id === keyId || item.key === keyId ? { ...item, isExhausted: true } : item);
  localStorage.setItem(storageKey, JSON.stringify(updated));
}

export function resetExhaustedKeys() {
  const gPool = getGeminiKeys().map(item => ({ ...item, isExhausted: false }));
  localStorage.setItem('thoughtdrop_gemini_pool', JSON.stringify(gPool));

  const groqPool = getGroqKeys().map(item => ({ ...item, isExhausted: false }));
  localStorage.setItem('thoughtdrop_groq_pool', JSON.stringify(groqPool));
}

/**
 * Main Refinement Entrypoint
 */
export async function refineTextWithAI(rawText, userCategory = 'Idea') {
  const engine = getSelectedEngine();
  const preferredModel = getSelectedModel();

  if (engine === 'groq') {
    return await refineWithGroq(rawText, userCategory);
  }

  // Engine is 'auto' or 'gemini'
  try {
    return await refineWithGemini(rawText, userCategory, preferredModel);
  } catch (geminiError) {
    console.warn("Gemini refinement failed:", geminiError);
    if (engine === 'auto') {
      const groqKeys = getGroqKeys().filter(k => !k.isExhausted);
      if (groqKeys.length > 0) {
        console.log("⚡ Auto-failing over to Groq Cloud (Llama 3)...");
        return await refineWithGroq(rawText, userCategory);
      }
    }
    throw geminiError;
  }
}

/**
 * Gemini Refinement with Multi-Key Rotation and Model Fallback
 */
async function refineWithGemini(rawText, userCategory, preferredModel) {
  const pool = getGeminiKeys();
  if (pool.length === 0) {
    throw new Error("No Gemini API keys found. Sign into Google (top-right) to sync from your phone or add a key in Settings (⚙).");
  }

  const promptText = `
You are an elite thinking partner and executive note taker.
Analyze the following written note / thoughts and refine it into structured, highly readable sections.

User suggested category (optional): "${userCategory}"

Note content:
"""
${rawText}
"""

Return JSON with EXACTLY these fields:
- "title": A memorable, punchy title (max 5-7 words) that captures the core concept.
- "category": One vibrant single-word tag (e.g. "Work", "Idea", "Project", "Strategy", "Finance", "Personal", "Creative", "Health").
- "summary": A crisp 2-3 sentence executive synthesis of what was written.
- "keyPoints": Array of 2 to 5 clear bullet points breaking down details, items, or reasoning mentioned.
- "actionItems": Array of concrete checkable next steps or to-do items (can be empty [] only if purely reflective with zero actions).

Return ONLY raw valid JSON matching this schema. No markdown code blocks, no backticks, no extra text.
`.trim();

  const requestBody = {
    contents: [{ parts: [{ text: promptText }] }],
    generationConfig: {
      temperature: 0.2,
      responseMimeType: "application/json"
    }
  };

  // Model candidate hierarchy: requested model first, then compatible fallbacks
  const modelCandidates = [
    preferredModel,
    'gemini-4-hydrogen',
    'gemini-3.8-flash',
    'gemini-2.0-flash',
    'gemini-1.5-flash'
  ].filter((v, i, a) => a.indexOf(v) === i); // remove duplicates

  let lastError = null;

  for (const entry of pool) {
    if (entry.isExhausted) continue;

    for (const modelName of modelCandidates) {
      try {
        const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${entry.key}`;
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(requestBody)
        });

        if (!response.ok) {
          const errorData = await response.json().catch(() => ({}));
          const errMsg = errorData.error?.message || `HTTP ${response.status}`;

          // Check if quota exhausted (429 / ResourceExhausted)
          if (response.status === 429 || errMsg.toLowerCase().includes('quota') || errMsg.toLowerCase().includes('resourceexhausted')) {
            console.warn(`Gemini key "${entry.label}" quota exhausted. Failing over to next key...`);
            markKeyExhausted('gemini', entry.id);
            break; // Break model loop, try next key in pool
          }

          // If model not found or deprecated, try next model candidate
          if (response.status === 404 || errMsg.toLowerCase().includes('not found') || errMsg.toLowerCase().includes('no longer available')) {
            console.warn(`Model ${modelName} unavailable (${errMsg}). Trying next candidate...`);
            continue;
          }

          throw new Error(errMsg);
        }

        const data = await response.json();
        const rawResponseText = data.candidates?.[0]?.content?.parts?.[0]?.text || '{}';
        const cleanedJson = rawResponseText
          .replace(/^```json\s*/i, '')
          .replace(/^```\s*/i, '')
          .replace(/```$/i, '')
          .trim();

        const parsed = JSON.parse(cleanedJson);
        return {
          title: parsed.title || "Refined Thought",
          category: parsed.category || userCategory || "Idea",
          summary: parsed.summary || rawText,
          keyPoints: Array.isArray(parsed.keyPoints) ? parsed.keyPoints : [],
          actionItems: Array.isArray(parsed.actionItems) ? parsed.actionItems : []
        };
      } catch (err) {
        lastError = err;
        // If error is not a model not-found error, try next key
        if (!err.message?.toLowerCase().includes('no longer available') && !err.message?.toLowerCase().includes('not found')) {
          break;
        }
      }
    }
  }

  throw lastError || new Error("All active Gemini keys in your pool failed or ran out of quota.");
}

/**
 * Groq Llama 3 Refinement (OpenAI-Compatible endpoint)
 */
async function refineWithGroq(rawText, userCategory) {
  const pool = getGroqKeys();
  if (pool.length === 0) {
    throw new Error("No Groq API keys found. Sign into Google (top-right) to sync from your phone or add a Groq key in Settings (⚙).");
  }

  const promptText = `
You are an elite thinking partner and executive note taker.
Analyze the following written note / thoughts and refine it into structured, highly readable sections.

User suggested category (optional): "${userCategory}"

Note content:
"""
${rawText}
"""

Return JSON with EXACTLY these fields:
- "title": A memorable, punchy title (max 5-7 words) that captures the core concept.
- "category": One vibrant single-word tag (e.g. "Work", "Idea", "Project", "Strategy", "Finance", "Personal", "Creative", "Health").
- "summary": A crisp 2-3 sentence executive synthesis of what was written.
- "keyPoints": Array of 2 to 5 clear bullet points breaking down details, items, or reasoning mentioned.
- "actionItems": Array of concrete checkable next steps or to-do items (can be empty [] only if purely reflective with zero actions).

Return ONLY raw valid JSON matching this schema. No markdown code blocks, no backticks, no extra text.
`.trim();

  const groqModels = ['llama-3.3-70b-versatile', 'llama-3.1-8b-instant'];

  for (const entry of pool) {
    if (entry.isExhausted) continue;

    for (const model of groqModels) {
      try {
        const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${entry.key}`
          },
          body: JSON.stringify({
            model: model,
            messages: [
              { role: 'system', content: 'You are an executive structured note taker. Return valid JSON only.' },
              { role: 'user', content: promptText }
            ],
            response_format: { type: 'json_object' },
            temperature: 0.2
          })
        });

        if (!response.ok) {
          const errData = await response.json().catch(() => ({}));
          const errMsg = errData.error?.message || `HTTP ${response.status}`;
          if (response.status === 429 || errMsg.toLowerCase().includes('rate_limit') || errMsg.toLowerCase().includes('quota')) {
            console.warn(`Groq key "${entry.label}" quota exhausted. Failing over...`);
            markKeyExhausted('groq', entry.id);
            break;
          }
          continue;
        }

        const data = await response.json();
        const content = data.choices?.[0]?.message?.content || '{}';
        const parsed = JSON.parse(content);
        return {
          title: parsed.title || "Refined Thought",
          category: parsed.category || userCategory || "Idea",
          summary: parsed.summary || rawText,
          keyPoints: Array.isArray(parsed.keyPoints) ? parsed.keyPoints : [],
          actionItems: Array.isArray(parsed.actionItems) ? parsed.actionItems : []
        };
      } catch (err) {
        console.warn(`Groq attempt with key ${entry.label} failed:`, err);
      }
    }
  }

  throw new Error("All Groq keys in your pool failed or ran out of quota.");
}
