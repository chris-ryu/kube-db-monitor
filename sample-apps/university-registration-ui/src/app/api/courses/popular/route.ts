import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.UNIVERSITY_API_URL || 'http://university-registration-demo-service:80';

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const params = new URLSearchParams(searchParams);
    
    const response = await fetch(`${BACKEND_URL}/api/courses/popular?${params}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error(`Backend responded with ${response.status}`);
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    console.error('Error fetching popular courses:', error);
    return NextResponse.json(
      { error: 'Failed to fetch popular courses' },
      { status: 500 }
    );
  }
}