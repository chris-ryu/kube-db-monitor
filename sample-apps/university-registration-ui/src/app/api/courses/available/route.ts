import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.UNIVERSITY_API_URL || 'http://university-registration-demo-service:80';

export async function GET(request: NextRequest) {
  try {
    const response = await fetch(`${BACKEND_URL}/api/courses/available`, {
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
    console.error('Error fetching available courses:', error);
    return NextResponse.json(
      { error: 'Failed to fetch available courses' },
      { status: 500 }
    );
  }
}