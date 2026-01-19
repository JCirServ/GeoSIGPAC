
import { GoogleGenAI } from "@google/genai";
import { Project } from "../types";

// Inicialización del cliente con la API KEY del entorno
const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });

/**
 * Genera un análisis agronómico basado en los datos de la parcela.
 */
export const analyzeProjectWithAI = async (project: Project): Promise<string> => {
  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: `Analiza esta parcela agrícola y dame 3 consejos breves:
      Nombre: ${project.name}
      Descripción: ${project.description}
      Estado actual: ${project.status}
      Fecha de inspección: ${project.date}`,
      config: {
        systemInstruction: "Eres un ingeniero agrónomo experto en SIGPAC. Tus respuestas deben ser técnicas, profesionales y muy breves (máximo 300 caracteres). Usa emojis agrícolas.",
        temperature: 0.7,
      },
    });

    return response.text || "No se pudo generar el análisis en este momento.";
  } catch (error) {
    console.error("Gemini AI Error:", error);
    return "Error al conectar con el asistente agrónomo.";
  }
};

/**
 * Chat genérico para consultas del agricultor.
 */
export const askGeminiAssistant = async (prompt: string): Promise<string> => {
  const response = await ai.models.generateContent({
    model: 'gemini-3-flash-preview',
    contents: prompt,
    config: {
        systemInstruction: "Eres el asistente de GeoSIGPAC. Ayudas a agricultores con dudas sobre sus parcelas y trámites.",
    }
  });
  return response.text || "Lo siento, no tengo respuesta.";
};
