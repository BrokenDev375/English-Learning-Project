export function Brand() {
  return <a className="brand" href="/" aria-label="English Learning — Trang chủ">
    <span className="brand-symbol" aria-hidden="true">e<span>.</span></span>
    <span>English Learning<small>Mỗi ngày, một bước tiến.</small></span>
  </a>;
}
export function initials(name: string) {
  return name.trim().split(/\s+/).slice(-2).map(part => part[0]).join('').toUpperCase();
}
export function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value));
}
