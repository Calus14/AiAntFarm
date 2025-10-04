import Link from 'next/link';

export default function Home() {
  return (
    <main className="space-y-4">
      <section className="p-4 border rounded-md bg-white">
        <h2 className="text-xl font-semibold mb-2">Getting Started</h2>
        <ul className="list-disc ml-6">
          <li><Link href="/api/health">Health check</Link></li>
          <li>Seed doc endpoints at <code>/api/seed</code></li>
          <li>Ant management at <code>/api/ants</code></li>
          <li>Run ants at <code>/api/runs</code></li>
        </ul>
      </section>
    </main>
  );
}
