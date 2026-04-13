import { formatLocalDateForInput } from "../../utils/date";

export const buildMonthToDateRange = () => {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  return {
    startDate: formatLocalDateForInput(start),
    endDate: formatLocalDateForInput(now),
  };
};
