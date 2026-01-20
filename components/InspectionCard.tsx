
import React from 'react';
import { Expediente } from '../types';
import { List, Trash2 } from 'lucide-react';

interface InspectionCardProps {
  expediente: Expediente;
  onDelete: (id: string) => void;
}

export const InspectionCard: React.FC<InspectionCardProps> = ({ expediente, onDelete }) => {
  const totalParcelas = expediente.parcelas.length;
  // Simulamos el progreso del 99% como en la imagen de ejemplo
  const progress = 99; 

  return (
    <div className="px-5 mb-5 group">
        <div className="neon-border animate-neon rounded-[28px] bg-[#0d0e1a] p-5">
            {/* Fila Superior: Título + Botones */}
            <div className="flex justify-between items-start mb-5">
                <div className="flex items-center gap-3">
                    {/* Punto indicador */}
                    <div className="w-2.5 h-2.5 rounded-full bg-[#5c60f5] shadow-[0_0_8px_rgba(92,96,245,0.8)]" />
                    <div>
                        <h3 className="text-white font-bold text-[17px] tracking-tight">
                            {expediente.titular} (1)
                        </h3>
                        <p className="text-gray-500 text-[11px] font-semibold mt-0.5">
                            {totalParcelas} parcelas • {totalParcelas} recintos
                        </p>
                    </div>
                </div>

                <div className="flex gap-2.5">
                    {/* Botón Detalles */}
                    <button className="w-10 h-10 flex items-center justify-center rounded-full bg-white/5 text-[#5c60f5] hover:bg-white/10 active:scale-90 transition-all">
                        <List size={20} strokeWidth={2.5} />
                    </button>
                    {/* Botón Borrar */}
                    <button 
                        onClick={() => onDelete(expediente.id)}
                        className="w-10 h-10 flex items-center justify-center rounded-full bg-white/5 text-gray-500 hover:text-red-400 hover:bg-red-500/10 active:scale-90 transition-all"
                    >
                        <Trash2 size={18} />
                    </button>
                </div>
            </div>

            {/* Fila Inferior: Barra + Texto de Estado */}
            <div className="space-y-2.5">
                <div className="h-[2px] w-full bg-white/5 rounded-full overflow-hidden">
                    <div 
                        className="h-full bg-[#5c60f5] shadow-[0_0_10px_rgba(92,96,245,0.6)] transition-all duration-1000 ease-out"
                        style={{ width: `${progress}%` }}
                    />
                </div>
                <div className="flex justify-end">
                    <p className="text-[10px] font-bold text-gray-500/80 tracking-tight">
                        Analizando SIGPAC... <span className="text-gray-400">{progress}%</span>
                    </p>
                </div>
            </div>
        </div>
    </div>
  );
};
