
import { GoogleGenAI } from "@google/genai";
import { Parcela } from "../types";

const SYSTEM_AGRONOMIST = `Eres un inspector técnico de la PAC (Política Agraria Común) en España. 
Tu trabajo es validar la coherencia de los datos declarados.
Analiza la referencia SIGPAC y el uso declarado.
Si el uso es coherente con una parcela agrícola estándar, responde "CONFORME: [Breve explicación]".
Si detectas algo raro (ej: cultivo tropical en zona de montaña, o uso desconocido), responde "INCIDENCIA: [Explicación]".
Sé extremadamente conciso. Máximo 20 palabras.`;

/**
 * Analiza una parcela específica para validar su declaración.
 */
export const analyzeParcelaCompliance = async (parcela: Parcela): Promise<string> => {
  try {
    const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });
    
    const prompt = `Analiza esta parcela:
    Ref: ${parcela.referencia}
    Uso Declarado: ${parcela.uso}
    Superficie: ${parcela.area} ha.
    ¿Es un uso agrícola válido y coherente?`;

    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        systemInstruction: SYSTEM_AGRONOMIST,
        temperature: 0.3, // Baja temperatura para respuestas más deterministas y técnicas
      }
    });
    
    return response.text?.trim() || "No se pudo generar el informe.";
  } catch (error) {
    console.error("Error AI Analysis:", error);
    return "Error de conexión con el servicio de análisis.";
  }
};

/**
 * Chat interactivo con el asistente agrónomo.
 */
export const askGeminiAssistant = async (prompt: string): Promise<string> => {
  try {
    const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });
    
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        systemInstruction: "Eres el asistente inteligente de GeoSIGPAC. Ayudas a agricultores con dudas sobre el visor SIGPAC, fotos georreferenciadas y normativa PAC de España. Responde de forma concisa y amable.",
      }
    });
    
    return response.text || "No tengo una respuesta para eso en este momento.";
  } catch (error) {
    console.error("Error Chat AI Studio:", error);
    return "Servicio de asistencia temporalmente no disponible.";
  }
};

// Legacy functions kept for compatibility if needed, but analyzeParcelaCompliance is preferred.
export const analyzeProjectWithAI = async (project: any): Promise<string> => {
    return "Función deprecada. Use análisis por parcela.";
};
export const analyzeProjectPhoto = async (photoBase64: string, projectName: string): Promise<string> => {
    return "Función pendiente de migración a Parcela.";
};
