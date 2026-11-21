/**
 * ThoughtDrop Web Studio - Gemini AI Text Refiner (BYOK)
 * Refines raw written notes into structured title, category, summary, bullet points, and action items.
 */

export async function refineTextWithAI(rawText, userCategory = 'Idea') {
  const apiKey = localStorage.getItem('thoughtdrop_gemini_key') || '';
  if (!apiKey) {
    throw new Error("Gemini API Key missing. Please click Settings (⚙) to add your Gemini API Key.");
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

  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;

  const requestBody = {
    contents: [
      {
        parts: [
          { text: promptText }
        ]
      }
    ],
    generationConfig: {
      temperature: 0.2,
      responseMimeType: "application/json"
    }
  };

  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(requestBody)
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    const message = errorData.error?.message || `Gemini API Error (Status ${response.status})`;
    throw new Error(message);
  }

  const data = await response.json();
  const rawResponseText = data.candidates?.[0]?.content?.parts?.[0]?.text || '{}';
  
  // Clean markdown if present
  const cleanedJson = rawResponseText
    .replace(/^```json\s*/i, '')
    .replace(/^```\s*/i, '')
    .replace(/```$/i, '')
    .trim();

  try {
    const parsed = JSON.parse(cleanedJson);
    return {
      title: parsed.title || "Refined Thought",
      category: parsed.category || userCategory || "Idea",
      summary: parsed.summary || rawText,
      keyPoints: Array.isArray(parsed.keyPoints) ? parsed.keyPoints : [],
      actionItems: Array.isArray(parsed.actionItems) ? parsed.actionItems : []
    };
  } catch (err) {
    console.error("Failed to parse Gemini JSON:", rawResponseText, err);
    throw new Error("Failed to format note from AI response.");
  }
}
