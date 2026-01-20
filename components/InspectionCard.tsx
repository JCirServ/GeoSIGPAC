
import React from 'react';
import { Expediente } from '../types';
import { List, Trash2 } from 'lucide-react';

interface InspectionCardProps {
  expediente: Expediente;
  onDelete: (id: string) => void;
}

export const InspectionCard: React.FC<InspectionCardProps> = ({ expediente, onDelete }) => {
  const totalParcelas = expediente.parcelas.length;
  // Simulamos progreso
  const progress = 0; 

  return (
    <div className="neon-border rounded-[24px] bg-[#0d0e1a] p-5">
        <div className="flex justify-between items-center mb-5">
            <div className="flex items-center gap-4">
                <div className="w-3 h-3 rounded-full bg-[#5c60f5] shadow-[0_0_12px_rgba(92,96,245,0.8)]" />
                <div className="flex flex-col">
                    <h3 className="text-white font-bold text-[17px] leading-tight">
                        {expediente.titular} (1)
                    </h3>
                    <p className="text-gray-500 text-[13px] font-semibold mt-0.5">
                        {totalParcelas} parcelas • {totalParcelas} recintos
                    </p>
                </div>
            </div>

            <div className="flex gap-2.5">
                <button className="w-11 h-11 flex items-center justify-center rounded-full bg-white/5 text-[#5c60f5] hover:bg-white/10 transition-colors">
                    <List size={22} />
                </button>
                <button 
                    onClick={() => onDelete(expediente.id)}
                    className="w-11 h-11 flex items-center justify-center rounded-full bg-white/5 text-gray-500 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                >
                    <Trash2 size={20} />
                </button>
            </div>
        </div>

        <div className="space-y-3">
            <div className="h-[2px] w-full bg-white/5 rounded-full overflow-hidden">
                <div 
                    className="h-full bg-[#00ff88] transition-all duration-1000"
                    style={{ width: `${progress}%` }}
                />
            </div>
            <div className="flex justify-end">
                <p className="text-[11px] font-bold text-[#00ff88] uppercase tracking-wider">
                    {progress}% Completado
                </p>
            </div>
        </div>
    </div>
  );
};
