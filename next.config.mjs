/** @type {import('next').NextConfig} */
const nextConfig = {
  /* standard production build config */
  async redirects() {
    return [
      // the beginner guide moved from its sub-route to the main Steamworks page (2026-10-25);
      // the Karakuri Arm programming guide moved the other way, to /steamworks/arm
      { source: '/steamworks/how-it-works', destination: '/steamworks', permanent: true },
      // the /brewing mini-game page was retired 2026-10-25 (the in-game Sakagura plugin
      // + the /sakagura guide page cover brewing now); old bookmarks keep working
      { source: '/brewing', destination: '/sakagura', permanent: true },
    ];
  },
};

export default nextConfig;
