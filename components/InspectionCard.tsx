
import React from 'react';
import { Expediente } from '../types';
import { List, Trash2 } from 'lucide-react';

interface InspectionCardProps {
  expediente: Expediente;
  onDelete: (id: string) => void;
}

export const InspectionCard: React.FC<InspectionCardProps> = ({ expediente, onDelete }) => {
  const totalParcelas = expediente.parcelas.length;
  // Simulamos progreso basado en el estado
  const progress = 99; 

  return (
    <div className="relative px-4 mb-4">
        <div className="neon-border rounded-[24px] bg-[#0d0e1a]/90 p-5 overflow-hidden">
            <div className="flex justify-between items-start mb-4">
                <div className="flex items-center gap-3">
                    <div className="w-2.5 h-2.5 rounded-full bg-indigo-500 shadow-[0_0_8px_rgba(99,102,241,0.8)]" />
                    <div>
                        <h3 className="text-white font-bold text-lg leading-tight">
                            {expediente.titular} ({expediente.id.slice(-1) === '1' ? '1' : '1'}) 
                        </h3>
                        <p className="text-gray-500 text-xs font-medium">
                            {totalParcelas} parcelas • {totalParcelas} recintos
                        </p>
                    </div>
                </div>

                <div className="flex gap-2">
                    <button className="w-10 h-10 flex items-center justify-center rounded-full bg-white/5 text-indigo-400 hover:bg-white/10 transition-colors">
                        <List size={20} />
                    </button>
                    <button 
                        onClick={() => onDelete(expediente.id)}
                        className="w-10 h-10 flex items-center justify-center rounded-full bg-white/5 text-gray-500 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                    >
                        <Trash2 size={18} />
                    </button>
                </div>
            </div>

            <div className="space-y-2">
                <div className="h-1.5 w-full bg-white/5 rounded-full overflow-hidden">
                    <div 
                        className="h-full bg-indigo-500 shadow-[0_0_10px_rgba(99,102,241,0.6)] transition-all duration-1000"
                        style={{ width: `${progress}%` }}
                    />
                </div>
                <div className="flex justify-end">
                    <p className="text-[10px] font-bold text-gray-500 tracking-wider">
                        Analizando SIGPAC... <span className="text-gray-400">{progress}%</span>
                    </p>
                </div>
            </div>
        </div>
    </div>
  );
};
