
import React, { useRef, useState } from 'react';
import { FileDown, Loader2 } from 'lucide-react';
import { Expediente, Parcela } from '../types';
import { showNativeToast } from '../services/bridge';
import JSZip from 'jszip';

interface KmlUploaderProps {
  onDataParsed: (expediente: Expediente) => void;
}

export const KmlUploader: React.FC<KmlUploaderProps> = ({ onDataParsed }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isParsing, setIsParsing] = useState(false);

  const parseKmlContent = (kmlText: string, fileName: string): Expediente => {
    const parser = new DOMParser();
    // Fix: replaced 'val' with 'const' to correct syntax error
    const xmlDoc = parser.parseFromString(kmlText, "text/xml");
    const placemarks = xmlDoc.getElementsByTagName("Placemark");
    const parcelas: Parcela[] = [];

    for (let i = 0; i < placemarks.length; i++) {
      const pm = placemarks[i];
      const metadata: Record<string, string> = {};
      
      // Extraer ExtendedData
      const dataElements = pm.getElementsByTagName("Data");
      for (let j = 0; j < dataElements.length; j++) {
        const name = dataElements[j].getAttribute("name");
        const value = dataElements[j].getElementsByTagName("value")[0]?.textContent || "";
        if (name) metadata[name] = value;
      }

      // Coordenadas
      let lat = 40, lng = -3;
      const coords = pm.getElementsByTagName("coordinates")[0]?.textContent?.trim();
      if (coords) {
        const firstPoint = coords.split(/\s+/)[0].split(",");
        if (firstPoint.length >= 2) {
          lng = parseFloat(firstPoint[0]);
          lat = parseFloat(firstPoint[1]);
        }
      }

      const ref = metadata["Ref_SigPac"] || pm.getElementsByTagName("name")[0]?.textContent || `Recinto ${i + 1}`;
      
      parcelas.push({
        id: `p-${Date.now()}-${i}`,
        referencia: ref,
        uso: metadata["USO_SIGPAC"] || metadata["USO"] || "N/D",
        lat,
        lng,
        area: parseFloat(metadata["DN_SURFACE"] || metadata["Superficie"] || "0"),
        status: 'pendiente'
      });
    }

    return {
      id: `exp-${Date.now()}`,
      titular: fileName.replace(/\.(kml|kmz)$/i, ''),
      campana: 2024,
      fechaImportacion: new Date().toLocaleDateString(),
      descripcion: "Importación desde archivo",
      status: 'en_curso',
      parcelas
    };
  };

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setIsParsing(true);
    try {
      const buffer = await file.arrayBuffer();
      const view = new Uint8Array(buffer);
      const isZip = view.length > 4 && view[0] === 0x50 && view[1] === 0x4B;

      let kmlText = "";
      if (isZip || file.name.toLowerCase().endsWith('.kmz')) {
        const zip = await JSZip.loadAsync(buffer);
        const kmlFile = Object.keys(zip.files).find(n => n.toLowerCase().endsWith('.kml'));
        if (!kmlFile) throw new Error("El archivo KMZ no contiene un archivo .kml válido.");
        kmlText = await zip.file(kmlFile)!.async("string");
      } else {
        kmlText = new TextDecoder("utf-8").decode(buffer);
      }

      const expediente = parseKmlContent(kmlText, file.name);
      
      if (expediente.parcelas.length === 0) {
        throw new Error("No se encontraron recintos válidos en el archivo.");
      }

      onDataParsed(expediente);
      showNativeToast(`Importados ${expediente.parcelas.length} recintos.`);
    } catch (e: any) {
      showNativeToast("Error al procesar: " + e.message);
    } finally {
      setIsParsing(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  return (
    <div className="px-4 mb-6">
      <input type="file" ref={fileInputRef} onChange={handleFileUpload} accept=".kml,.kmz" className="hidden" />
      <button 
        onClick={() => fileInputRef.current?.click()}
        disabled={isParsing}
        className="w-full h-40 flex flex-col items-center justify-center border-2 border-dashed border-white/10 rounded-[32px] bg-white/[0.02] hover:bg-white/[0.05] transition-all"
      >
        {isParsing ? (
          <Loader2 className="animate-spin text-neon-blue mb-2" size={32} />
        ) : (
          <div className="mb-3 text-neon-blue">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
                <line x1="12" y1="18" x2="12" y2="12"></line>
                <polyline points="9 15 12 12 15 15"></polyline>
            </svg>
          </div>
        )}
        <p className="text-white font-bold text-sm">Cargar KML / KMZ de Inspección</p>
        <p className="text-gray-500 text-xs mt-1">
          {isParsing ? "Procesando cartografía..." : "Soporte completo para ficheros comprimidos"}
        </p>
      </button>
    </div>
  );
};
