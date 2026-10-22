const GRADIENTS = [
  ['#ff5a1f', '#ff2e63'],
  ['#7b5cff', '#3fa7ff'],
  ['#00b386', '#0089e0'],
  ['#ff9f0a', '#ff4d1f'],
  ['#e0287a', '#7a35e8'],
  ['#14b88a', '#1a8ae6'],
  ['#ef4f4f', '#e09a16'],
  ['#3a8dff', '#00b8d4'],
];

/** Cor fixa por tag, para o botão não mudar de cor a cada visita. */
export function gradientFor(id: number): string {
  const [a, b] = GRADIENTS[Math.abs(id) % GRADIENTS.length];
  return `linear-gradient(135deg, ${a}, ${b})`;
}
