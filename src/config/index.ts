import dotenv from "dotenv";
import path from "path";

dotenv.config({ path: path.resolve(process.cwd(), ".env") });

export interface BotConfig {
  host: string;
  port: number;
  version: string;
  username: string;
  auth: "microsoft" | "offline";
  minProfitMargin: number;
  minProfitAmount: number;
  maxSpendPerItem: number;
  priceHistorySampleSize: number;
  simulationMode: boolean;
  clickDelayMin: number;
  clickDelayMax: number;
  ahRefreshInterval: number;
}

export const config: BotConfig = {
  host: process.env.MC_HOST || "donutsmp.net",
  port: parseInt(process.env.MC_PORT || "25565", 10),
  version: process.env.MC_VERSION || "1.20.4",
  username: process.env.MC_USERNAME || "DonutTrader",
  auth: (process.env.MC_AUTH as "microsoft" | "offline") || "microsoft",
  minProfitMargin: parseFloat(process.env.MIN_PROFIT_MARGIN || "0.20"),
  minProfitAmount: parseFloat(process.env.MIN_PROFIT_AMOUNT || "50000"),
  maxSpendPerItem: parseFloat(process.env.MAX_SPEND_PER_ITEM || "5000000"),
  priceHistorySampleSize: parseInt(process.env.PRICE_HISTORY_SAMPLE_SIZE || "20", 10),
  simulationMode: process.env.SIMULATION_MODE === "true" || process.env.SIMULATION_MODE === undefined,
  clickDelayMin: parseInt(process.env.CLICK_DELAY_MIN || "250", 10),
  clickDelayMax: parseInt(process.env.CLICK_DELAY_MAX || "600", 10),
  ahRefreshInterval: parseInt(process.env.AH_REFRESH_INTERVAL || "1500", 10)
};
