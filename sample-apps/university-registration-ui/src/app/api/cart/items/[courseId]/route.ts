import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.UNIVERSITY_API_URL || 'http://university-registration-demo-service:80';

export async function DELETE(
  request: NextRequest,
  { params }: { params: { courseId: string } }
) {
  try {
    const { courseId } = params;
    const { searchParams } = new URL(request.url);
    const urlParams = new URLSearchParams(searchParams);
    
    const response = await fetch(`${BACKEND_URL}/api/cart/items/${courseId}?${urlParams}`, {
      method: 'DELETE',
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
    console.error('Error removing from cart:', error);
    return NextResponse.json(
      { error: 'Failed to remove from cart' },
      { status: 500 }
    );
  }
}