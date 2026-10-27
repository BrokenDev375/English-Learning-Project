import { useId } from 'react';

export function GameKey() {
  return <svg className="game-key-icon" viewBox="0 0 32 32" aria-hidden="true"><circle cx="12" cy="10" r="6" fill="none" stroke="currentColor" strokeWidth="3" /><path d="M12 16v13m0-6h7m-7 6h5" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" /></svg>;
}

export function Explorer({ celebrating = false }: { celebrating?: boolean }) {
  return <g className={celebrating ? 'explorer celebrating' : 'explorer'}>
    <ellipse cx="0" cy="38" rx="22" ry="5" fill="#254746" opacity=".16" />
    <path d="M-11 13l-5 22M10 13l8 22" stroke="#344c65" strokeWidth="11" strokeLinecap="round" />
    <path d="M-15 36h-8M17 36h8" stroke="#203c49" strokeWidth="8" strokeLinecap="round" />
    <path d="M-13-9l-15 15M12-9L27-20" stroke="#edaf74" strokeWidth="10" strokeLinecap="round" />
    <rect x="-17" y="-18" width="34" height="39" rx="12" fill="#e99743" />
    <path d="M-7-16l5 36M8-16L3 20" stroke="#52694b" strokeWidth="5" />
    <circle cy="-35" r="21" fill="#f4be86" /><path d="M-20-41q1-25 24-17q18 2 17 21l-8-7-8 5-12-8z" fill="#614938" />
    <path d="M-21-50q8-24 36-9l7 15" fill="#d6b45e" /><path d="M-31-44q33-9 62 1" stroke="#b68a40" strokeWidth="9" strokeLinecap="round" />
    <circle cx="-7" cy="-35" r="2" fill="#364642" /><circle cx="8" cy="-35" r="2" fill="#364642" />
    <path d="M-5-25q6 5 12-1" fill="none" stroke="#aa6447" strokeWidth="2.5" strokeLinecap="round" />
    <circle cx="30" cy="-22" r="7" fill="#d9b749" /><path d="M30-15v12m-5-4h5" stroke="#d9b749" strokeWidth="4" strokeLinecap="round" />
  </g>;
}

export function TreasureChest({ open = false, small = false }: { open?: boolean; small?: boolean }) {
  const id = useId();
  return <svg className={'treasure-chest' + (open ? ' is-open' : '') + (small ? ' small' : '')} viewBox="0 0 240 210" aria-hidden="true">
    <defs><linearGradient id={id} x1="0" y1="0" x2="0" y2="1"><stop stopColor="#ffd978" /><stop offset="1" stopColor="#ca8630" /></linearGradient></defs>
    <ellipse cx="120" cy="189" rx="97" ry="12" fill="#244d3c" opacity=".15" />
    <g className="chest-glow"><ellipse cx="120" cy="105" rx="85" ry="77" fill="#ffe68b" opacity=".5" /><path d="M120 12v35M42 43l28 26M200 43l-27 26M18 108h35M221 108h-34" stroke="#f8d15c" strokeWidth="5" strokeLinecap="round" /></g>
    <path d="M36 111h168l-8 64q-2 9-12 9H55q-10-1-12-10z" fill="#9d613c" stroke="#68472f" strokeWidth="5" />
    <path d="M46 137h149M49 160h143" stroke="#75472f" strokeWidth="4" />
    <path d="M61 112v70M177 112v70" stroke={`url(#${id})`} strokeWidth="17" />
    <ellipse cx="120" cy="113" rx="77" ry="13" fill="#4d402b" />
    <g className="chest-treasure"><circle cx="91" cy="109" r="13" fill="#ffd85e" /><circle cx="119" cy="102" r="14" fill="#f7bb32" /><circle cx="147" cy="110" r="13" fill="#ffe08c" /><path d="M121 84l9 12-9 13-9-13z" fill="#56bea7" /></g>
    <g className="chest-lid"><path d="M35 111V89q1-40 42-40h85q42 1 43 40v22z" fill="#b87847" stroke="#68472f" strokeWidth="5" /><path d="M37 87h165M64 53v58M176 53v58" stroke={`url(#${id})`} strokeWidth="13" /><path d="M101 103h38v39h-38z" fill={`url(#${id})`} stroke="#9d6e2d" strokeWidth="3" /><circle cx="120" cy="117" r="5" fill="#654a2c" /><path d="M120 118v10" stroke="#654a2c" strokeWidth="5" /></g>
    {open && <g className="chest-sparkles" fill="#f4c847"><path d="M24 69l4 10 10 4-10 4-4 10-4-10-10-4 10-4zM211 35l4 9 9 4-9 4-4 9-4-9-9-4 9-4zM184 8l3 7 7 3-7 3-3 7-3-7-7-3 7-3z" /></g>}
  </svg>;
}

export function IslandScene({ progress = 0, open = false }: { progress?: number; open?: boolean }) {
  const id = useId(), x = 86 + Math.max(0, Math.min(1, progress)) * 560;
  return <svg className={'island-scene' + (open ? ' complete' : '')} viewBox="0 0 800 230" aria-hidden="true">
    <defs><linearGradient id={id} x2="0" y2="1"><stop stopColor="#d9eef0" /><stop offset="1" stopColor="#f0f5dd" /></linearGradient></defs>
    <rect width="800" height="230" rx="24" fill={`url(#${id})`} />
    <circle cx="670" cy="47" r="29" fill="#ffe09a" /><path d="M0 145l102-99 107 111L329 71l127 96L568 83l112 80 120-65v132H0z" fill="#b9d6bb" />
    <path d="M0 176q135-67 258-12t283-2 259 8v60H0" fill="#a5c69b" />
    <path d="M0 209q129-26 268-9t299-3 233 13v20H0" fill="#80b9b3" />
    <path d="M49 177q150 26 296-5t368 3" fill="none" stroke="#f1dda1" strokeWidth="21" strokeLinecap="round" />
    <path d="M49 177q150 26 296-5t368 3" fill="none" stroke="#c49c5d" strokeWidth="2" strokeDasharray="5 11" />
    {[160, 330, 500].map(n => <g key={n}><ellipse cx={n} cy="176" rx="15" ry="7" fill="#e3c584" /><path d={`M${n} 156v20m-5-14h10`} stroke="#e6af3e" strokeWidth="4" strokeLinecap="round" /></g>)}
    <g transform="translate(721 137)"><path d="M-34 14v30h66V14zM-35 14V3q0-21 22-21h26q22 0 22 21v11z" fill="#bc7c43" stroke="#825332" strokeWidth="4" /><path d="M-20-15v58M19-15v58" stroke="#eec45d" strokeWidth="8" /><rect x="-6" y="8" width="14" height="17" rx="2" fill="#ffe094" /></g>
    <g transform={`translate(${x} 143) scale(.69)`}><Explorer celebrating={open} /></g>
    <path d="M40 154V82m0 9q-26-25-31-8m31 8q20-30 32-21m-32 21q31-9 39 9" stroke="#5f916c" strokeWidth="10" strokeLinecap="round" fill="none" />
    <path d="M765 131V80m0 8q-20-22-29-11m29 11q16-27 27-19" stroke="#668c58" strokeWidth="9" strokeLinecap="round" fill="none" />
  </svg>;
}

export function BridgeScene({ placed, total, open = false }: { placed: number; total: number; open?: boolean }) {
  const count = Math.max(1, total), plank = 442 / count;
  return <svg className={'bridge-scene' + (open ? ' is-open' : '')} data-open={open} viewBox="0 0 800 225" aria-hidden="true">
    <rect width="800" height="225" rx="20" fill="#e2f1ef" /><path d="M147 78q84 27 178-3t197 7 132-9v152H143z" fill="#83c9d1" /><path d="M179 195q92-24 183 0t205 0" stroke="#c9eef0" fill="none" strokeWidth="7" />
    <path d="M0 108q76-39 161 3v114H0M647 109q74-37 153-5v121H645" fill="#b3ce91" />
    <path d="M155 104q238 33 496 0M155 160q238 27 496 0" fill="none" stroke="#a78b58" strokeWidth="5" />
    {Array.from({ length: count }, (_, i) => <rect key={i} x={177 + i * plank} y="128" width={Math.max(4, plank - 6)} height="41" rx="4" fill={i < placed ? '#dba64f' : '#b9dce0'} stroke={i < placed ? '#9e743b' : '#78b8c3'} strokeDasharray={i < placed ? undefined : '5 4'} strokeWidth="2" />)}
    <g transform={`translate(${open ? 709 : 99} 117) scale(.75)`}><g className={open ? 'bridge-crossing' : undefined}><Explorer celebrating={open} /></g></g>
    <path d="M702 72V18m0 0l36 12-36 11" fill="#dfac54" stroke="#a57839" strokeWidth="4" strokeLinejoin="round" />
  </svg>;
}
