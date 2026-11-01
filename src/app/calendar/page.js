import React from 'react';
import CalendarClient from './CalendarClient';

export const metadata = {
  title: 'Calendar — Iekcorbar',
  description: 'Upcoming community events, the holidays the server celebrates and the seasons of the Sakagura garden.',
};

export default function CalendarPage() {
  return <CalendarClient />;
}
