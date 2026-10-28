export function roundHeading(roundNumber?: number) {
  return roundNumber ? 'Vòng đấu ' + roundNumber : 'Vòng đấu';
}
export function roundOption(roundNumber: number, title: string) {
  return roundHeading(roundNumber) + ' · ' + title;
}
