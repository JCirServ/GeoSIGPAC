
import { GoogleGenAI } from "@google/genai";
import { Project } from "../types";

/**
 * Servicio de Inteligencia Artificial para GeoSIGPAC.
 * Utiliza los modelos de Google AI Studio (Gemini).
 */

/**
 * Genera un análisis agronómico basado en los datos de la parcela.
 */
export const analyzeProjectWithAI = async (project: Project): Promise<string> => {
  try {
    // Instanciamos el cliente justo antes de la llamada
    const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });
    
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: `Analiza esta parcela agrícola y dame 3 consejos técnicos breves para el cuaderno de campo:
      Nombre: ${project.name}
      Descripción: ${project.description}
      Estado actual: ${project.status}
      Fecha: ${project.date}`,
      config: {
        systemInstruction: "Eres un ingeniero agrónomo experto en SIGPAC y normativa PAC en España. Sé muy técnico, profesional y breve. Máximo 250 caracteres.",
        temperature: 0.7,
      },
    });

    // La propiedad .text extrae el contenido generado directamente
    return response.text || "No se ha podido generar el análisis técnico.";
  } catch (error) {
    console.error("Error AI Studio:", error);
    return "Error al conectar con Google AI Studio. Verifica la API Key.";
  }
};

/**
 * Analiza una imagen capturada por el agricultor para detectar problemas.
 */
export const analyzeProjectPhoto = async (photoBase64: string, projectName: string): Promise<string> => {
  try {
    const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });

    const response = await ai.models.generateContent({
      model: 'gemini-2.5-flash-image',
      contents: {
        parts: [
          { text: `Identifica el estado de salud de este cultivo en la parcela ${projectName} y detecta anomalías (plagas, clorosis, estrés hídrico).` },
          { 
            inlineData: { 
              mimeType: "image/jpeg", 
              data: photoBase64.includes(',') ? photoBase64.split(',')[1] : photoBase64 
            } 
          }
        ]
      }
    });

    return response.text || "No se pudo analizar la imagen visualmente.";
  } catch (error) {
    console.error("Error Vision AI Studio:", error);
    return "Error en el análisis visual de la imagen.";
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
