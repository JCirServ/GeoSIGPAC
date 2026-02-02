
import { GoogleGenAI } from "@google/genai";
import { Parcela, Expediente, Project } from "../types";

/**
 * Auditoría completa de un expediente.
 * Gemini analiza la coherencia de todas las parcelas en conjunto.
 */
export const auditFullExpediente = async (expediente: Expediente): Promise<string> => {
  try {
    const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });
    
    const summary = expediente.parcelas.map(p => 
      `- Ref: ${p.referencia}, Uso: ${p.uso}, Area: ${p.area}ha, Status: ${p.status}`
    ).join('\n');

    const prompt = `Como auditor senior de la PAC, analiza la coherencia de este expediente:
    Titular: ${expediente.titular}
    Parcelas declaradas:
    ${summary}
    
    Busca:
    1. Fragmentación excesiva.
    2. Usos sospechosos (ej: mucho barbecho).
    3. Incoherencias de superficie.
    Responde con un diagnóstico técnico breve (máx 40 palabras).`;

    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        systemInstruction: "Eres un auditor antifraude de ayudas agrícolas europeas. Tu tono es crítico, técnico y extremadamente directo.",
        temperature: 0.2,
      }
    });
    
    // Accessing .text property directly as per guidelines
    return response.text?.trim() || "Auditoría no disponible.";
  } catch (error) {
    return "Error en el motor de auditoría.";
  }
};

/**
 * Analiza un proyecto individual y proporciona una recomendación técnica.
 */
// Fix: Added missing analyzeProjectWithAI function for ProjectCard component
export const analyzeProjectWithAI = async (project: Project): Promise<string> => {
  try {
    const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });
    const prompt = `Analiza este proyecto agrícola: Nombre: ${project.name}, Descripción: ${project.description}, Status: ${project.status}.`;
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        systemInstruction: "Eres un experto en gestión de proyectos agrícolas. Proporciona una recomendación breve y profesional basada en los datos del proyecto.",
      }
    });
    // Accessing .text property directly as per guidelines
    return response.text?.trim() || "Análisis no disponible.";
  } catch (error) {
    return "Error al analizar el proyecto.";
  }
};

export const analyzeParcelaCompliance = async (parcela: Parcela): Promise<string> => {
  try {
    const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });
    const prompt = `Analiza esta parcela: Ref: ${parcela.referencia}, Uso: ${parcela.uso}, Area: ${parcela.area} ha.`;
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        systemInstruction: "Valida la coherencia técnica del uso agrícola. Sé conciso.",
      }
    });
    // Accessing .text property directly as per guidelines
    return response.text?.trim() || "Informe pendiente.";
  } catch (error) {
    return "Error de análisis.";
  }
};

export const askGeminiAssistant = async (prompt: string): Promise<string> => {
  try {
    const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        systemInstruction: "Asistente inteligente GeoSIGPAC. Responde dudas sobre normativa PAC.",
      }
    });
    // Accessing .text property directly as per guidelines
    return response.text || "No disponible.";
  } catch (error) {
    return "Error de comunicación.";
  }
};
