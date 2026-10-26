// /story-room is reached through private guest links — keep it out of
// search engines, and never leak the address (with its ?key=) as a referrer.
export const metadata = {
  title: 'Story Room',
  robots: { index: false, follow: false },
  referrer: 'no-referrer'
};

export default function StoryRoomLayout({ children }) {
  return children;
}
