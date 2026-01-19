
import { GoogleGenAI } from "@google/genai";
import { Project } from "../types";

/**
 * Inicializa la IA. 
 * Se asume que process.env.API_KEY está disponible en el entorno de ejecución.
 */
const getAIClient = () => new GoogleGenAI({ apiKey: process.env.API_KEY });

/**
 * Genera un análisis agronómico basado en los datos de la parcela.
 */
export const analyzeProjectWithAI = async (project: Project): Promise<string> => {
  try {
    const ai = getAIClient();
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: `Analiza esta parcela agrícola y dame 3 consejos breves:
      Nombre: ${project.name}
      Descripción: ${project.description}
      Estado actual: ${project.status}
      Fecha de inspección: ${project.date}`,
      config: {
        systemInstruction: "Eres un ingeniero agrónomo experto en SIGPAC y PAC. Tus respuestas deben ser técnicas, profesionales y muy breves (máximo 300 caracteres). Usa emojis agrícolas. Si la parcela está pendiente, indica qué evidencia fotográfica suele faltar.",
        temperature: 0.7,
      },
    });

    return response.text || "No se pudo generar el análisis en este momento.";
  } catch (error) {
    console.error("Gemini AI Error:", error);
    return "Error al conectar con el asistente agrónomo. Comprueba tu conexión.";
  }
};

/**
 * Chat genérico para consultas del agricultor sobre normativa o técnica.
 */
export const askGeminiAssistant = async (prompt: string): Promise<string> => {
  try {
    const ai = getAIClient();
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        systemInstruction: "Eres el asistente inteligente de GeoSIGPAC. Ayudas a agricultores de España con dudas sobre SIGPAC, fotos georreferenciadas y trámites de la PAC. Sé conciso y amable.",
      }
    });
    return response.text || "Lo siento, no he podido procesar tu consulta.";
  } catch (error) {
    console.error("Assistant Error:", error);
    return "Error de comunicación con el servidor de IA.";
  }
};
