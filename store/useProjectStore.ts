
import { useState, useCallback, useEffect } from 'react';
import { Expediente, Parcela } from '../types';
import { syncInspectionWithNative } from '../services/bridge';

const STORAGE_KEY = 'geosigpac_expedientes_v3';

export const useProjectStore = () => {
  const [expedientes, setExpedientes] = useState<Expediente[]>([]);
  const [loading, setLoading] = useState(true);

  // Cargar datos persistidos
  useEffect(() => {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      setExpedientes(JSON.parse(saved));
    }
    setLoading(false);
  }, []);

  // Persistir cambios
  useEffect(() => {
    if (!loading) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(expedientes));
    }
  }, [expedientes, loading]);

  const addExpediente = (expediente: Expediente) => {
    setExpedientes(prev => [expediente, ...prev]);
    // Sincronización automática con Android al importar
    syncInspectionWithNative(expediente);
  };

  const removeExpediente = (id: string) => {
    setExpedientes(prev => prev.filter(i => i.id !== id));
  };

  const updateParcelaStatus = (expId: string, parcelaId: string, status: Parcela['status']) => {
    setExpedientes(prev => prev.map(exp => {
      if (exp.id === expId) {
        return {
          ...exp,
          parcelas: exp.parcelas.map(p => p.id === parcelaId ? { ...p, status } : p)
        };
      }
      return exp;
    }));
  };

  return { expedientes, addExpediente, removeExpediente, updateParcelaStatus, loading };
};
