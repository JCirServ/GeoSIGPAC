
import React, { useRef, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Expediente, Parcela } from '../types';
import { showNativeToast } from '../services/bridge';
import JSZip from 'jszip';

interface KmlUploaderProps {
  onDataParsed: (expediente: Expediente) => void;
}

export const KmlUploader: React.FC<KmlUploaderProps> = ({ onDataParsed }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isParsing, setIsParsing] = useState(false);

  const parseKml = (kmlText: string): Parcela[] => {
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(kmlText, "text/xml");
    const placemarks = xmlDoc.getElementsByTagName("Placemark");
    const parsedParcelas: Parcela[] = [];

    for (let i = 0; i < placemarks.length; i++) {
      const p = placemarks[i];
      const metadata: Record<string, string> = {};
      
      // Extraer ExtendedData
      const extendedData = p.getElementsByTagName("ExtendedData")[0];
      if (extendedData) {
        const dataNodes = extendedData.getElementsByTagName("Data");
        for (let j = 0; j < dataNodes.length; j++) {
          const name = dataNodes[j].getAttribute("name");
          const value = dataNodes[j].getElementsByTagName("value")[0]?.textContent || "";
          if (name) metadata[name] = value;
        }
      }

      // Extraer Coordenadas (Punto o Polígono)
      let lat = 0, lng = 0;
      const point = p.getElementsByTagName("Point")[0];
      const polygon = p.getElementsByTagName("Polygon")[0];
      
      if (point) {
        const coords = point.getElementsByTagName("coordinates")[0]?.textContent?.trim().split(",");
        if (coords) {
          lng = parseFloat(coords[0]);
          lat = parseFloat(coords[1]);
        }
      } else if (polygon) {
        const coordsText = polygon.getElementsByTagName("coordinates")[0]?.textContent?.trim() || "";
        const firstCoord = coordsText.split(/\s+/)[0].split(",");
        lng = parseFloat(firstCoord[0]);
        lat = parseFloat(firstCoord[1]);
      }

      const refSigPac = metadata["Ref_SigPac"] || `ID-${i}`;
      
      // Solo añadimos si tiene referencia (evitamos carpetas o nodos vacíos)
      if (metadata["Ref_SigPac"]) {
        parsedParcelas.push({
          id: `p-${Date.now()}-${i}`,
          referencia: refSigPac,
          uso: metadata["USO_SIGPAC"] || "N/A",
          area: parseFloat(metadata["DN_SURFACE"] || "0"),
          lat,
          lng,
          status: 'pendiente',
          metadata
        });
      }
    }

    return parsedParcelas;
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
      if (isZip) {
        const zip = await JSZip.loadAsync(buffer);
        const kmlFile = Object.keys(zip.files).find(n => n.toLowerCase().endsWith('.kml'));
        if (!kmlFile) throw new Error("El KMZ no contiene archivo .kml");
        kmlText = await zip.file(kmlFile)!.async("string");
      } else {
        kmlText = new TextDecoder("utf-8").decode(buffer);
      }

      const parcelas = parseKml(kmlText);

      if (parcelas.length === 0) {
        throw new Error("No se encontraron recintos válidos en el archivo.");
      }

      const expediente: Expediente = {
        id: `exp-${Date.now()}`,
        titular: file.name.replace(/\.(kml|kmz)$/i, ''),
        campana: 2024,
        fechaImportacion: new Date().toLocaleDateString(),
        descripcion: `Importado de ${file.name}`,
        status: 'pendiente',
        parcelas
      };
      
      onDataParsed(expediente);
      showNativeToast(`Importados ${parcelas.length} recintos.`);
    } catch (e: any) {
      showNativeToast("Error: " + e.message);
      console.error(e);
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
          <Loader2 className="animate-spin text-indigo-400 mb-2" size={32} />
        ) : (
          <div className="mb-3 text-indigo-400">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
                <line x1="12" y1="18" x2="12" y2="12"></line>
                <polyline points="9 15 12 12 15 15"></polyline>
            </svg>
          </div>
        )}
        <p className="text-white font-bold text-sm">{isParsing ? 'Procesando...' : 'Importar KML / KMZ'}</p>
        <p className="text-gray-500 text-xs mt-1">Soporta archivos de Google Earth</p>
      </button>
    </div>
  );
};
