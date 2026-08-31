import { config } from "./config";
import { logger } from "./utils/logger";

logger.info("DonutSMP Trader başlatılıyor...");
logger.info(`Hedef Sunucu: ${config.host}:${config.port} (Sürüm: ${config.version})`);
logger.info(`Simülasyon Modu: ${config.simulationMode ? "AÇIK (Gerçek alım yapılmaz)" : "KAPALI (Gerçek alım yapılır)"}`);
logger.info(`Minimum Kâr Marjı: %${config.minProfitMargin * 100} (Min Kâr: $${config.minProfitAmount.toLocaleString()})`);
