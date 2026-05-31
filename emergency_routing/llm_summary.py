import ollama


def generate_summary(crash, plan):

    dispatch_context = f"""
    Severity: {crash.severity}
    Event Type: {crash.event_type}

    Ambulance:
    {plan.get("ambulance")}

    Hospital:
    {plan.get("hospital")}

    Route:
    {plan.get("route_eta")}

    Police:
    {plan.get("police")}

    Tow:
    {plan.get("tow")}

    Notes:
    {plan.get("notes")}
    """

    prompt = f"""
You are an emergency dispatch summarizer.

Generate a human-readable summary
using ONLY the exact dispatch data.

IMPORTANT:
- Do NOT infer
- Do NOT assume
- Do NOT add new information
- Do NOT mention anything not explicitly present
- If a field is null or unavailable,
  explicitly say unavailable
- One short paragraph only
- Maximum 60 words
- Do not assume responders have arrived

Use ONLY these values:

Severity: {crash.severity}
Event Type: {crash.event_type}
Ambulance: {plan.get("ambulance")}
Hospital: {plan.get("hospital")}
Route ETA: {plan.get("route_eta")}
Police: {plan.get("police")}
Tow: {plan.get("tow")}
Notes: {plan.get("notes")}
"""

    response = ollama.chat(
        model="llama3.2:3b",
        messages=[
            {
                "role": "user",
                "content": prompt
            }
        ]
    )

    return response["message"]["content"].strip().replace('"', '')