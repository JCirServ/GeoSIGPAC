
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

      // Mock de parseo rápido para visualización
      const expediente: Expediente = {
        id: `exp-${Date.now()}`,
        titular: file.name.replace(/\.(kml|kmz)$/i, ''),
        campana: 2024,
        fechaImportacion: new Date().toLocaleDateString(),
        descripcion: "Importación manual",
        status: 'en_curso',
        parcelas: Array(357).fill(null).map((_, i) => ({
            id: `p-${i}`,
            referencia: `Parcela ${i}`,
            uso: 'TA',
            lat: 40, lng: -3, area: 1.5, status: 'pendiente'
        }))
      };
      
      onDataParsed(expediente);
      showNativeToast("Archivo importado correctamente.");
    } catch (e: any) {
      showNativeToast("Error: " + e.message);
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
        <p className="text-white font-bold text-sm">Importar KML / KMZ</p>
        <p className="text-gray-500 text-xs mt-1">Toque para seleccionar archivo</p>
      </button>
    </div>
  );
};
