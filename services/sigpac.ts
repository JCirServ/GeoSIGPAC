
import { SigpacInfo, CultivoInfo } from '../types';

/**
 * Parsea la referencia SIGPAC (soporta guiones o dos puntos)
 */
const parseRef = (ref: string) => {
  const parts = ref.split(/[-:]/);
  return {
    prov: parts[0],
    mun: parts[1],
    pol: parts[4] || parts[2],
    par: parts[5] || parts[3],
    rec: parts[6] || parts[4]
  };
};

export const fetchParcelaDetails = async (referencia: string): Promise<{ sigpac?: SigpacInfo, cultivo?: CultivoInfo }> => {
  const { prov, mun, pol, par, rec } = parseRef(referencia);
  
  const baseUrl = 'https://sigpac-hubcloud.es/ogcapi/collections';
  const query = `provincia=${prov}&municipio=${mun}&poligono=${pol}&parcela=${par}&recinto=${rec}&f=json`;

  try {
    const [resRecinto, resCultivo] = await Promise.all([
      fetch(`${baseUrl}/recintos/items?${query}`).then(r => r.json()),
      fetch(`${baseUrl}/cultivo_declarado/items?${query}`).then(r => r.json())
    ]);

    const sigpac: SigpacInfo | undefined = resRecinto.features?.[0]?.properties ? {
      pendiente: resRecinto.features[0].properties.pendiente_media,
      altitud: resRecinto.features[0].properties.altitud,
      municipio: resRecinto.features[0].properties.municipio,
      poligono: resRecinto.features[0].properties.poligono,
      parcela: resRecinto.features[0].properties.parcela,
      recinto: resRecinto.features[0].properties.recinto,
      provincia: resRecinto.features[0].properties.provincia
    } : undefined;

    const cultivo: CultivoInfo | undefined = resCultivo.features?.[0]?.properties ? {
      expNum: resCultivo.features[0].properties.exp_num,
      producto: resCultivo.features[0].properties.parc_producto,
      sistExp: resCultivo.features[0].properties.parc_sistexp === 'R' ? 'Regadío' : 'Secano',
      ayudaSol: resCultivo.features[0].properties.parc_ayudasol,
      superficie: resCultivo.features[0].properties.parc_supcult / 10000
    } : undefined;

    return { sigpac, cultivo };
  } catch (error) {
    console.error("Error fetching SIGPAC data:", error);
    return {};
  }
};
