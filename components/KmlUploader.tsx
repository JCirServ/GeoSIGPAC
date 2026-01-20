
import React, { useRef, useState } from 'react';
import { FileUp, Loader2, FileCheck } from 'lucide-react';
import { Expediente, Parcela } from '../types';
import { showNativeToast } from '../services/bridge';
import JSZip from 'jszip';

interface KmlUploaderProps {
  onDataParsed: (expediente: Expediente) => void;
}

export const KmlUploader: React.FC<KmlUploaderProps> = ({ onDataParsed }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isParsing, setIsParsing] = useState(false);

  const readFileAsArrayBuffer = (file: File): Promise<ArrayBuffer> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as ArrayBuffer);
      reader.onerror = () => reject(new Error("Error leyendo archivo"));
      reader.readAsArrayBuffer(file);
    });
  };

  const parseKmlContent = (kmlText: string, fileName: string): Expediente => {
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(kmlText, "text/xml");
    
    if (xmlDoc.getElementsByTagName("parsererror").length > 0) {
      throw new Error("XML inválido");
    }

    const placemarks = Array.from(xmlDoc.getElementsByTagName("Placemark"));
    const parcelas: Parcela[] = [];

    placemarks.forEach((pm, index) => {
      // Intentar extraer nombre y descripción
      let name = pm.getElementsByTagName("name")[0]?.textContent || `Recinto ${index + 1}`;
      
      // Intentar extraer referencia SIGPAC del nombre o descripción si existe patrón
      // Patrón simple: 46:123:0:0:1
      const sigpacRegex = /(\d{1,2}:\d{1,3}:\d{1,3}:\d{1,4}(?::\d{1,4})?)/;
      const match = name.match(sigpacRegex);
      const referencia = match ? match[0] : name;

      // Extraer coordenadas (Solo el primer punto para centrar el mapa)
      const coordsNode = pm.getElementsByTagName("coordinates")[0];
      if (coordsNode && coordsNode.textContent) {
        const rawCoords = coordsNode.textContent.trim().split(/\s+/)[0];
        const [lng, lat] = rawCoords.split(',').map(Number);

        if (!isNaN(lat) && !isNaN(lng)) {
            // Calcular área simulada basada en complejidad del polígono (si existiera)
            // En producción, calcular área real del polígono
            const area = Math.round((Math.random() * 5 + 0.5) * 100) / 100;

            parcelas.push({
                id: `parc-${Date.now()}-${index}`,
                referencia: referencia,
                uso: 'Pendiente', // Se podría extraer del extendedData
                lat,
                lng,
                area,
                status: 'pendiente'
            });
        }
      }
    });

    if (parcelas.length === 0) throw new Error("No se encontraron geometrías válidas");

    return {
        id: `exp-${Date.now()}`,
        titular: fileName.replace(/\.(kml|kmz)$/i, ''),
        campana: new Date().getFullYear(),
        fechaImportacion: new Date().toISOString().split('T')[0],
        descripcion: `Importado KML con ${parcelas.length} recintos declarados.`,
        status: 'pendiente',
        parcelas
    };
  };

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setIsParsing(true);
    try {
      const buffer = await readFileAsArrayBuffer(file);
      const view = new Uint8Array(buffer);
      const isZip = view.length > 4 && view[0] === 0x50 && view[1] === 0x4B; // PK header

      let kmlText = "";
      if (isZip) {
        const zip = await JSZip.loadAsync(buffer);
        const kmlFile = Object.keys(zip.files).find(n => n.toLowerCase().endsWith('.kml'));
        if (!kmlFile) throw new Error("El KMZ no contiene archivo .kml");
        kmlText = await zip.file(kmlFile)!.async("string");
      } else {
        kmlText = new TextDecoder("utf-8").decode(buffer);
      }

      const expediente = parseKmlContent(kmlText, file.name);
      onDataParsed(expediente);
      showNativeToast(`Expediente ${expediente.titular} importado.`);

    } catch (e: any) {
      console.error(e);
      showNativeToast("Error: " + e.message);
    } finally {
      setIsParsing(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  return (
    <div className="mb-6">
      <input 
        type="file" 
        ref={fileInputRef} 
        onChange={handleFileUpload} 
        accept=".kml,.kmz"
        className="hidden" 
      />
      <button 
        onClick={() => fileInputRef.current?.click()}
        disabled={isParsing}
        className="group w-full flex items-center justify-center gap-3 py-6 border-2 border-dashed border-emerald-500/30 rounded-2xl bg-gradient-to-br from-emerald-500/5 to-emerald-900/10 hover:bg-emerald-500/10 transition-all active:scale-[0.99]"
      >
        {isParsing ? (
          <Loader2 size={24} className="animate-spin text-emerald-500" />
        ) : (
          <div className="bg-emerald-500/20 p-3 rounded-full group-hover:scale-110 transition-transform">
             <FileUp size={24} className="text-emerald-500" />
          </div>
        )}
        <div className="text-left">
            <p className="font-bold text-slate-200 text-sm">
                {isParsing ? 'Procesando geometrías...' : 'Importar Expediente (KML/KMZ)'}
            </p>
            <p className="text-[10px] text-slate-500 font-medium uppercase tracking-wider">
                Sincronización automática con Mapa
            </p>
        </div>
      </button>
    </div>
  );
};
