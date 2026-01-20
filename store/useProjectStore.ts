
import { useState, useCallback, useEffect } from 'react';
import { Expediente, Parcela, SigpacInfo, CultivoInfo } from '../types';
import { syncInspectionWithNative } from '../services/bridge';

const STORAGE_KEY = 'geosigpac_expedientes_v3';

export const useProjectStore = () => {
  const [expedientes, setExpedientes] = useState<Expediente[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      setExpedientes(JSON.parse(saved));
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    if (!loading) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(expedientes));
    }
  }, [expedientes, loading]);

  const addExpediente = (expediente: Expediente) => {
    setExpedientes(prev => [expediente, ...prev]);
    syncInspectionWithNative(expediente);
  };

  const removeExpediente = (id: string) => {
    setExpedientes(prev => prev.filter(i => i.id !== id));
  };

  const updateParcelaData = (expId: string, parcelaId: string, data: { sigpac?: SigpacInfo, cultivo?: CultivoInfo }) => {
    setExpedientes(prev => prev.map(exp => {
      if (exp.id === expId) {
        return {
          ...exp,
          parcelas: exp.parcelas.map(p => p.id === parcelaId ? { 
            ...p, 
            sigpacData: data.sigpac, 
            cultivoData: data.cultivo,
            isDataLoaded: true 
          } : p)
        };
      }
      return exp;
    }));
  };

  const setParcelaReport = (expId: string, parcelaId: string, report: string) => {
    setExpedientes(prev => prev.map(exp => {
      if (exp.id === expId) {
        return {
            ...exp,
            parcelas: exp.parcelas.map(p => {
                if (p.id === parcelaId) {
                    const newStatus = report.toUpperCase().includes("INCIDENCIA") ? 'incidencia' : 'conforme';
                    return { ...p, aiReport: report, status: newStatus };
                }
                return p;
            })
        };
      }
      return exp;
    }));
  };

  return { expedientes, addExpediente, removeExpediente, updateParcelaData, setParcelaReport, loading };
};
